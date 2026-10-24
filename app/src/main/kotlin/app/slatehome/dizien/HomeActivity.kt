package app.slatehome.dizien

import android.app.WallpaperManager
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.setValue

class HomeActivity : ComponentActivity(), HomeHost {
    private lateinit var store: Store
    private lateinit var repo: AppRepo
    private var resumeTick by mutableLongStateOf(0L)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(SystemBarStyle.dark(Color.TRANSPARENT), SystemBarStyle.dark(Color.TRANSPARENT))
        if (android.os.Build.VERSION.SDK_INT >= 29) window.isNavigationBarContrastEnforced = false
        store = Store.get(this)
        repo = AppRepo.get(this)
        setContent {
            DiZienTheme(paletteById(store.look.themeId)) {
                HomeScreen(store, repo, this, wallVersion, resumeTick)
            }
        }
    }

    private val wallVersion: Long get() = Wallpapers.customFile(this).lastModified()

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        // Home button pressed while we are already the foreground launcher: go back to the first page.
        if (intent.hasCategory(Intent.CATEGORY_HOME) == true) resumeTick++
    }

    override fun onResume() {
        super.onResume()
        repo.reload()
    }

    override fun launch(item: LItem) {
        when {
            item.app != null -> repo.launch(item.app)
            item.key == Virtual.TERMINAL -> openTerminal()
            item.key == Virtual.LOOK -> openLook()
            item.key == Virtual.SETTINGS -> openSettings()
        }
    }

    private fun openTerminal() {
        val i = TerminalLink.intent(this, store.terminalPkg)
        if (i != null) { try { startActivity(i) } catch (_: Exception) { toast(this, "Could not open the terminal app") } }
        else {
            startActivity(Intent(this, SettingsActivity::class.java).putExtra(SettingsActivity.EXTRA_NO_TERMINAL, true))
        }
    }

    override fun chooseTerminal() = openTerminal()

    override fun expandNotifications() {
        try {
            val sb = getSystemService("statusbar")
            Class.forName("android.app.StatusBarManager").getMethod("expandNotificationsPanel").invoke(sb)
        } catch (_: Throwable) { }
    }

    override fun openLook() = startActivity(Intent(this, LookActivity::class.java))
    override fun openSettings() = startActivity(Intent(this, SettingsActivity::class.java))

    // ---- Widgets ----
    private var pendingId = -1
    private var pendingPage = 0
    override fun onStart() { super.onStart(); Widgets.listen(this) }
    override fun onStop() { super.onStop(); Widgets.stop(this) }

    override fun addWidget(info: android.appwidget.AppWidgetProviderInfo, page: Int) {
        try {
            val id = Widgets.host(this).allocateAppWidgetId()
            pendingId = id; pendingPage = page
            val ok = try { Widgets.awm(this).bindAppWidgetIdIfAllowed(id, info.provider) } catch (_: Throwable) { false }
            if (ok) afterBind() else {
                val i = Intent(android.appwidget.AppWidgetManager.ACTION_APPWIDGET_BIND)
                    .putExtra(android.appwidget.AppWidgetManager.EXTRA_APPWIDGET_ID, id)
                    .putExtra(android.appwidget.AppWidgetManager.EXTRA_APPWIDGET_PROVIDER, info.provider)
                @Suppress("DEPRECATION") startActivityForResult(i, REQ_BIND)
            }
        } catch (_: Throwable) { cancelPending(); toast(this, "Could not add this widget") }
    }
    private fun afterBind() {
        val id = pendingId
        val info = try { Widgets.awm(this).getAppWidgetInfo(id) } catch (_: Throwable) { null }
        if (info?.configure != null) {
            try { Widgets.host(this).startAppWidgetConfigureActivityForResult(this, id, 0, REQ_CONFIG, null); return }
            catch (_: Throwable) { /* fall through: add without configuring */ }
        }
        finishAdd()
    }
    private fun finishAdd() {
        val id = pendingId; if (id < 0) return
        val info = try { Widgets.awm(this).getAppWidgetInfo(id) } catch (_: Throwable) { null }
        if (info == null) { cancelPending(); return }
        store.addWidget(pendingPage, id, Widgets.defaultHeightDp(this, info))
        pendingId = -1
        toast(this, "Widget added")
    }
    private fun cancelPending() {
        if (pendingId >= 0) { try { Widgets.host(this).deleteAppWidgetId(pendingId) } catch (_: Throwable) { } }
        pendingId = -1
    }
    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        @Suppress("DEPRECATION") super.onActivityResult(requestCode, resultCode, data)
        when (requestCode) {
            REQ_BIND -> if (resultCode == RESULT_OK) afterBind() else cancelPending()
            REQ_CONFIG -> if (resultCode == RESULT_OK) finishAdd() else cancelPending()
        }
    }
    override fun removeWidget(id: Int) {
        store.removeWidget(id)
        try { Widgets.host(this).deleteAppWidgetId(id) } catch (_: Throwable) { }
    }
    override fun lockScreen() {
        if (!ScreenLock.lock(this)) {
            toast(this, "Turn on a lock method in Settings > Gestures")
            startActivity(Intent(this, SettingsActivity::class.java).putExtra(SettingsActivity.EXTRA_OPEN, "lock"))
        }
    }
    companion object { private const val REQ_BIND = 7001; private const val REQ_CONFIG = 7002 }

    override fun requestDefaultHome() {
        try { startActivity(homeSettingsIntent(this)) }
        catch (_: Exception) { try { startActivity(Intent(Settings.ACTION_SETTINGS)) } catch (_: Exception) { } }
    }

    override fun openWebSearch(q: String) {
        // No Internet permission: we only hand the query to another app (the user's default browser / search app).
        val nt = Intent.FLAG_ACTIVITY_NEW_TASK
        try {
            startActivity(Intent(Intent.ACTION_WEB_SEARCH).putExtra(android.app.SearchManager.QUERY, q).addFlags(nt))
            return
        } catch (_: Exception) { }
        try {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://www.google.com/search?q=" + Uri.encode(q))).addFlags(nt))
        } catch (_: Exception) {
            android.widget.Toast.makeText(this, "No browser found to open the search", android.widget.Toast.LENGTH_SHORT).show()
        }
    }
}

/** Sets the system wallpaper to the same image the launcher shows (optional, user-controlled). */
fun applySystemWallpaper(act: android.content.Context, id: Int) {
    try {
        val wm = WallpaperManager.getInstance(act)
        val dm = act.resources.displayMetrics
        val bmp = Wallpapers.forId(act, id, dm.widthPixels, dm.heightPixels)
        wm.setBitmap(bmp)
    } catch (_: Exception) { }
}

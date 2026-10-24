package app.slatehome.dizien

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.LauncherActivityInfo
import android.content.pm.LauncherApps
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.os.UserHandle
import android.os.UserManager
import android.provider.AlarmClock
import android.provider.MediaStore
import android.provider.Settings
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

class AppEntry(
    val key: String,
    val label: String,
    val pkg: String,
    val cls: String,
    val user: UserHandle,
    val info: LauncherActivityInfo,
    val isSystem: Boolean,
)

/** Something that can sit on the home screen, dock or in search: an installed app or a built-in shortcut. */
class LItem(val key: String, val label: String, val app: AppEntry?, val glyph: String?)

object Virtual {
    const val TERMINAL = "v:terminal"
    const val LOOK = "v:look"
    const val SETTINGS = "v:settings"
    val all = listOf(
        LItem(TERMINAL, "Terminal", null, "term"),
        LItem(LOOK, "Look", null, "palette"),
        LItem(SETTINGS, "Launcher", null, "gear"),
    )
    fun get(key: String) = all.firstOrNull { it.key == key }
}

/**
 * The separate Debian-style terminal app is not part of this launcher. It can opt in by declaring an activity with
 * action MAIN and category [CATEGORY]; the user can also pick any app in Settings.
 */
object TerminalLink {
    const val CATEGORY = "app.slatehome.dizien.category.TERMINAL"

    fun intent(ctx: Context, preferredPkg: String): Intent? {
        val pm = ctx.packageManager
        if (preferredPkg.isNotBlank()) {
            pm.getLaunchIntentForPackage(preferredPkg)?.let { return it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
        }
        val q = Intent(Intent.ACTION_MAIN).addCategory(CATEGORY)
        val ri = pm.queryIntentActivities(q, 0).firstOrNull() ?: return null
        return Intent(q).setClassName(ri.activityInfo.packageName, ri.activityInfo.name)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
}

class AppRepo private constructor(private val ctx: Context) {
    private val la = ctx.getSystemService(Context.LAUNCHER_APPS_SERVICE) as LauncherApps
    private val um = ctx.getSystemService(Context.USER_SERVICE) as UserManager
    private val main = Handler(Looper.getMainLooper())

    var apps by mutableStateOf<List<AppEntry>>(emptyList()); private set
    var loaded by mutableStateOf(false); private set

    private val callback = object : LauncherApps.Callback() {
        override fun onPackageRemoved(packageName: String?, user: UserHandle?) = scheduleReload()
        override fun onPackageAdded(packageName: String?, user: UserHandle?) = scheduleReload()
        override fun onPackageChanged(packageName: String?, user: UserHandle?) = scheduleReload()
        override fun onPackagesAvailable(p: Array<out String>?, user: UserHandle?, replacing: Boolean) = scheduleReload()
        override fun onPackagesUnavailable(p: Array<out String>?, user: UserHandle?, replacing: Boolean) = scheduleReload()
    }
    private val reloadRunnable = Runnable { reload() }
    private fun scheduleReload() { main.removeCallbacks(reloadRunnable); main.postDelayed(reloadRunnable, 300) }

    init {
        la.registerCallback(callback, main)
        reload()
    }

    fun reload() {
        val out = ArrayList<AppEntry>()
        for (u in um.userProfiles) {
            val serial = um.getSerialNumberForUser(u)
            for (info in la.getActivityList(null, u)) {
                if (info.componentName.packageName == ctx.packageName) continue
                val flags = info.applicationInfo.flags
                val sys = (flags and ApplicationInfo.FLAG_SYSTEM) != 0 && (flags and ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) == 0
                out += AppEntry(
                    key = "${info.componentName.packageName}/${info.componentName.className}@$serial",
                    label = info.label?.toString() ?: info.componentName.packageName,
                    pkg = info.componentName.packageName,
                    cls = info.componentName.className,
                    user = u, info = info, isSystem = sys,
                )
            }
        }
        out.sortBy { it.label.lowercase() }
        apps = out
        loaded = true
    }

    fun find(key: String): LItem? {
        Virtual.get(key)?.let { return it }
        val a = apps.firstOrNull { it.key == key } ?: return null
        return LItem(a.key, a.label, a, null)
    }

    fun launch(app: AppEntry) {
        try {
            la.startMainActivity(ComponentName(app.pkg, app.cls), app.user, null, null)
        } catch (_: Exception) { }
    }

    fun appInfo(app: AppEntry) {
        try { la.startAppDetailsActivity(ComponentName(app.pkg, app.cls), app.user, null, null) } catch (_: Exception) { }
    }

    fun uninstall(app: AppEntry) {
        val i = Intent(Intent.ACTION_DELETE, Uri.parse("package:${app.pkg}"))
            .putExtra(Intent.EXTRA_USER, app.user).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        try { ctx.startActivity(i) } catch (_: Exception) { }
    }

    /** First run: choose sensible pins and dock apps from what this particular phone has installed. */
    fun seedDefaults(store: Store) {
        if (store.layoutSeeded || apps.isEmpty()) return
        val pm = ctx.packageManager
        fun byIntent(i: Intent): AppEntry? {
            val ri = try { pm.resolveActivity(i, PackageManager.MATCH_DEFAULT_ONLY) } catch (_: Exception) { null } ?: return null
            val pkg = ri.activityInfo?.packageName ?: return null
            if (pkg == "android") return null
            return apps.firstOrNull { it.pkg == pkg && it.user == android.os.Process.myUserHandle() }
        }
        fun byCat(cat: String) = byIntent(Intent.makeMainSelectorActivity(Intent.ACTION_MAIN, cat))
        fun byPkgPart(part: String) = apps.firstOrNull { it.pkg.contains(part) && it.user == android.os.Process.myUserHandle() }

        val phone = byIntent(Intent(Intent.ACTION_DIAL))
        val sms = byCat(Intent.CATEGORY_APP_MESSAGING)
        val browser = byCat(Intent.CATEGORY_APP_BROWSER)
        val camera = byIntent(Intent(MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA))
        val dockApps = listOfNotNull(phone, sms, browser, camera).distinctBy { it.key }
        val used = dockApps.map { it.key }.toMutableSet()

        val extra = listOfNotNull(
            byPkgPart("documentsui"),
            byCat(Intent.CATEGORY_APP_GALLERY),
            byCat(Intent.CATEGORY_APP_CONTACTS),
            byCat(Intent.CATEGORY_APP_MAPS),
            byCat(Intent.CATEGORY_APP_CALENDAR),
            byCat(Intent.CATEGORY_APP_EMAIL),
            byCat(Intent.CATEGORY_APP_MUSIC),
            byCat(Intent.CATEGORY_APP_CALCULATOR),
            byIntent(Intent(AlarmClock.ACTION_SHOW_ALARMS)),
            byPkgPart("com.android.vending"),
        ).distinctBy { it.key }.filter { used.add(it.key) }

        store.pins.clear()
        store.pins += Virtual.TERMINAL
        store.pins += extra.take(9).map { it.key }
        store.pins += Virtual.LOOK
        store.pins += Virtual.SETTINGS
        store.dock.clear()
        store.dock += dockApps.map { it.key }
        store.savePins(); store.saveDock(); store.markSeeded()
    }

    companion object {
        @Volatile private var inst: AppRepo? = null
        fun get(ctx: Context): AppRepo =
            inst ?: synchronized(this) { inst ?: AppRepo(ctx.applicationContext).also { inst = it } }
    }
}

fun isDefaultHome(ctx: Context): Boolean {
    val i = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
    val ri = ctx.packageManager.resolveActivity(i, PackageManager.MATCH_DEFAULT_ONLY)
    return ri?.activityInfo?.packageName == ctx.packageName
}

fun homeSettingsIntent(ctx: Context): Intent {
    if (android.os.Build.VERSION.SDK_INT >= 29) {
        try {
            val rm = ctx.getSystemService(Context.ROLE_SERVICE) as android.app.role.RoleManager
            if (rm.isRoleAvailable(android.app.role.RoleManager.ROLE_HOME) && !rm.isRoleHeld(android.app.role.RoleManager.ROLE_HOME)) {
                return rm.createRequestRoleIntent(android.app.role.RoleManager.ROLE_HOME)
            }
        } catch (_: Exception) { }
    }
    return Intent(Settings.ACTION_HOME_SETTINGS)
}

package app.slatehome.dizien

import android.content.Intent
import android.graphics.Color as AColor
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.DisposableEffect
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.compose.ui.platform.LocalLifecycleOwner

class SettingsActivity : ComponentActivity() {
    companion object { const val EXTRA_NO_TERMINAL = "no_terminal"; const val EXTRA_OPEN = "open" }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(SystemBarStyle.dark(AColor.TRANSPARENT), SystemBarStyle.dark(AColor.TRANSPARENT))
        if (android.os.Build.VERSION.SDK_INT >= 29) window.isNavigationBarContrastEnforced = false
        val store = Store.get(this)
        val repo = AppRepo.get(this)
        val noTerm = intent.getBooleanExtra(EXTRA_NO_TERMINAL, false)
        val openLock = intent.getStringExtra(EXTRA_OPEN) == "lock"
        setContent {
            DiZienTheme(paletteById(store.look.themeId)) { SettingsScreen(store, repo, this, noTerm, openLock) }
        }
    }
}

private enum class Overlay { NONE, LOCK, WPAGES, GRID, PAGES, TERMINAL, HIDDEN, LICENSES, ABOUT_LICENSE, NOTIF }

@Composable
fun SettingsScreen(store: Store, repo: AppRepo, act: ComponentActivity, noTerminalHint: Boolean, openLock: Boolean = false) {
    val pal = LocalPal.current
    SystemBarIcons(pal.light)
    val ctx = LocalContext.current
    var overlay by remember { mutableStateOf(if (noTerminalHint) Overlay.TERMINAL else if (openLock) Overlay.LOCK else Overlay.NONE) }
    var lockReady by remember { mutableStateOf(ScreenLock.ready(ctx)) }
    var sheet by remember { mutableStateOf<SheetModel?>(null) }
    var isDefault by remember { mutableStateOf(isDefaultHome(ctx)) }
    var notifAccess by remember { mutableStateOf(NotifState.isAccessGranted(ctx)) }
    var licenseName by remember { mutableStateOf("") }

    val owner = LocalLifecycleOwner.current
    DisposableEffect(owner) {
        val ob = LifecycleEventObserver { _, e -> if (e == Lifecycle.Event.ON_RESUME) { isDefault = isDefaultHome(ctx); notifAccess = NotifState.isAccessGranted(ctx); NotifState.ensureBound(ctx); lockReady = ScreenLock.ready(ctx); repo.reload() } }
        owner.lifecycle.addObserver(ob)
        onDispose { owner.lifecycle.removeObserver(ob) }
    }

    val termIntent = TerminalLink.intent(ctx, store.terminalPkg)
    val termApp = if (store.terminalPkg.isNotBlank()) repo.apps.firstOrNull { it.pkg == store.terminalPkg }?.label else null
    val sizeName = listOf("small", "medium", "large")[store.iconSize.coerceIn(0, 2)]

    Box(Modifier.fillMaxSize().background(pal.bg)) {
        Column(Modifier.fillMaxSize().statusBarsPadding()) {
            ScreenHeader("gear", "Settings", "DiZien Launcher ${BuildConfigCompat.version(ctx)}") {
                Pill((Build.SUPPORTED_ABIS.firstOrNull() ?: "").uppercase(), false)
            }
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 4.dp)) {
                SectionLabel("Home app")
                Panel {
                    SettingRow(
                        "home", "Default home app",
                        if (isDefault) "DiZien Launcher is your home app" else "Not set · Home button opens another launcher",
                        onClick = {
                            try { act.startActivity(homeSettingsIntent(ctx)) }
                            catch (_: Exception) { try { act.startActivity(Intent(Settings.ACTION_SETTINGS)) } catch (_: Exception) { } }
                        },
                    ) { Pill(if (isDefault) "On" else "Off", isDefault) }
                }

                SectionLabel("Launcher")
                Panel {
                    SettingRow("grid", "Grid & icon size", "${store.cols} × ${store.rows} · $sizeName", onClick = { overlay = Overlay.GRID }) {
                        GlyphIcon("chev", pal.sub, 20.dp)
                    }
                    RowDivider()
                    SettingRow("grid", "Home pages", "${store.currentPages()} pages · swipe left/right on Home", onClick = { overlay = Overlay.PAGES }) {
                        GlyphIcon("chev", pal.sub, 20.dp)
                    }
                    RowDivider()
                    SettingRow("plus", "Widget pages", if (store.widgetPages == 0) "None · swipe left from Home is off" else "${store.widgetPages} page(s) left of Home · ${store.widgets.size} widget(s)", onClick = { overlay = Overlay.WPAGES }) {
                        GlyphIcon("chev", pal.sub, 20.dp)
                    }
                    RowDivider()
                    SettingRow("dock", "Show clock", "Clock, date and status chips on Home") {
                        Toggle(store.showDockClock) { store.updateShowDockClock(it) }
                    }
                    RowDivider()
                    SettingRow("eye", "Show icon labels", "Names under icons on Home") {
                        Toggle(store.showLabels) { store.updateShowLabels(it) }
                    }
                    RowDivider()
                    SettingRow(
                        "bell", "Notification access",
                        if (notifAccess) "On · shown in the DiZien panel" else "Off · panel shows quick tiles only",
                        onClick = { overlay = Overlay.NOTIF },
                    ) { Pill(if (notifAccess) "On" else "Off", notifAccess) }
                    RowDivider()
                    SettingRow("eye", "Hidden apps", "${store.hidden.size} hidden from the drawer", onClick = { overlay = Overlay.HIDDEN }) {
                        GlyphIcon("chev", pal.sub, 20.dp)
                    }
                    RowDivider()
                    SettingRow("trash", "Reset home layout", "Re-pick the default apps and dock", onClick = {
                        store.resetLayout(); repo.seedDefaults(store); toast(ctx, "Home layout reset")
                    })
                }

                SectionLabel("Gestures")
                Panel {
                    SettingRow("chev", "Swipe up on Home", "Open the all-apps drawer") {
                        Toggle(store.gSwipeUp) { store.updateGSwipeUp(it) }
                    }
                    RowDivider()
                    SettingRow(
                        "bell", "Swipe down on Home",
                        listOf("DiZien notification panel", "Android notification shade", "Off", "Search apps")[store.swipeMode.coerceIn(0, 3)],
                        onClick = {
                            sheet = SheetModel("Swipe down on Home", "Choose what opens", listOf(
                                SheetAction("bell", "DiZien notification panel") { store.updateSwipeMode(0) },
                                SheetAction("dock", "Android notification shade") { store.updateSwipeMode(1) },
                                SheetAction("search", "Search apps") { store.updateSwipeMode(3) },
                                SheetAction("close", "Off") { store.updateSwipeMode(2) },
                            ))
                        },
                    ) { GlyphIcon("chev", pal.sub, 20.dp) }
                    RowDivider()
                    SettingRow(
                        "lock", "Double-tap to lock screen",
                        if (!store.gDoubleTap) "Off" else if (lockReady) "On · double-tap empty space on Home" else "Needs a permission · tap to set up",
                        onClick = { overlay = Overlay.LOCK },
                    ) { Toggle(store.gDoubleTap) { v -> store.updateGDoubleTap(v); if (v && !ScreenLock.ready(ctx)) overlay = Overlay.LOCK } }
                    RowDivider()
                    SettingRow(
                        "palette", "Long-press empty space",
                        listOf("Home menu", "Open Look & wallpaper", "Off")[store.gLongPress.coerceIn(0, 2)],
                        onClick = {
                            sheet = SheetModel("Long-press empty space", "Choose what opens", listOf(
                                SheetAction("grid", "Home menu") { store.updateGLongPress(0) },
                                SheetAction("palette", "Look & wallpaper") { store.updateGLongPress(1) },
                                SheetAction("close", "Off") { store.updateGLongPress(2) },
                            ))
                        },
                    ) { GlyphIcon("chev", pal.sub, 20.dp) }
                    RowDivider()
                    SettingRow("home", "Home button on Home", "Always jumps back to the main page")
                }
                SectionLabel("Terminal")
                Panel {
                    SettingRow(
                        "term", "Terminal app",
                        when {
                            termApp != null -> "Linked: $termApp"
                            termIntent != null -> "Found automatically"
                            else -> "Not linked · separate app"
                        },
                        onClick = { overlay = Overlay.TERMINAL },
                    ) { GlyphIcon("chev", pal.sub, 20.dp) }
                }
                Txt(
                    "The Debian-style terminal is a separate app. This launcher only opens it if it is installed; it contains no terminal or Linux files itself.",
                    size = 11.5f, color = pal.sub, modifier = Modifier.padding(start = 4.dp, top = 8.dp),
                )

                SectionLabel("About")
                Panel {
                    Column(Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Image(painterResource(R.drawable.about_icon), null, Modifier.size(52.dp).clip(RoundedCornerShape(12.dp)))
                            Column(Modifier.padding(start = 14.dp)) {
                                Txt("DiZien Launcher", size = 17f, bold = true)
                                Txt("Version ${BuildConfigCompat.version(ctx)} · open source", size = 12.5f, color = pal.sub)
                            }
                        }
                        Row(Modifier.padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Pill("GPL-3.0", true); Pill("no root", false); Pill("no internet", false)
                        }
                    }
                    RowDivider()
                    SettingRow("info", "Open-source licenses", "GPL-3.0, DejaVu fonts, Debian logo", onClick = { overlay = Overlay.LICENSES }) {
                        GlyphIcon("chev", pal.sub, 20.dp)
                    }
                    RowDivider()
                    // Debian reference tile: the official Open Use Logo is allowed here, with the required notice.
                    Column(Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                Modifier.size(52.dp).clip(RoundedCornerShape(6.dp)).background(Color.White).padding(6.dp),
                                contentAlignment = Alignment.Center,
                            ) { Image(painterResource(R.drawable.debian_open_use_logo), "Debian Open Use Logo", Modifier.fillMaxSize(), contentScale = ContentScale.Fit) }
                            Column(Modifier.padding(start = 14.dp)) {
                                Txt("Debian style, not Debian", size = 14f, bold = true)
                                Txt("Look inspired by the Debian world", size = 11.5f, color = pal.sub)
                            }
                        }
                        Txt(
                            "Not affiliated with Debian. Debian is a registered trademark owned by Software in the Public Interest, Inc.",
                            size = 11.5f, color = pal.text, modifier = Modifier.padding(top = 12.dp),
                        )
                        Txt(
                            "Logo: Debian Open Use Logo, © 1999 Software in the Public Interest, Inc., used under LGPL-3.0-or-later or CC BY-SA 3.0. See debian.org/logos.",
                            size = 11f, color = pal.sub, modifier = Modifier.padding(top = 6.dp),
                        )
                    }
                }
                Box(Modifier.height(20.dp))
            }
            NavBar("settings") { d ->
                navigate(act, d, "settings") {
                    sheet = SheetModel("Terminal app not linked", NO_TERMINAL_TEXT, listOf(SheetAction("grid", "Choose a terminal app") { overlay = Overlay.TERMINAL }))
                }
            }
            Box(Modifier.navigationBarsPadding())
        }

        when (overlay) {
            Overlay.GRID -> GridEditor(store) { overlay = Overlay.NONE }
            Overlay.PAGES -> PagesEditor(store) { overlay = Overlay.NONE }
            Overlay.WPAGES -> WidgetPagesEditor(store) { overlay = Overlay.NONE }
            Overlay.LOCK -> LockSetup(store, lockReady) { overlay = Overlay.NONE }
            Overlay.TERMINAL -> TerminalPicker(store, repo) { overlay = Overlay.NONE }
            Overlay.HIDDEN -> HiddenPicker(store, repo) { overlay = Overlay.NONE }
            Overlay.NOTIF -> NotifAccessInfo(notifAccess) { overlay = Overlay.NONE }
            Overlay.LICENSES -> LicensesList({ overlay = Overlay.NONE }) { licenseName = it; overlay = Overlay.ABOUT_LICENSE }
            Overlay.ABOUT_LICENSE -> LicenseText(licenseName) { overlay = Overlay.LICENSES }
            Overlay.NONE -> {}
        }
        sheet?.let { s -> ActionSheet(s.title, s.subtitle, s.actions) { sheet = null } }
    }
    BackHandler(enabled = overlay != Overlay.NONE) {
        overlay = if (overlay == Overlay.ABOUT_LICENSE) Overlay.LICENSES else Overlay.NONE
    }
}

object BuildConfigCompat {
    fun version(ctx: android.content.Context): String =
        try { ctx.packageManager.getPackageInfo(ctx.packageName, 0).versionName ?: "" } catch (_: Exception) { "" }
}

@Composable
private fun FullOverlay(title: String, subtitle: String, onBack: () -> Unit, content: @Composable () -> Unit) {
    val pal = LocalPal.current
    Column(
        Modifier.fillMaxSize().background(pal.bg).statusBarsPadding()
            .clickable(remember { MutableInteractionSource() }, null) { },
    ) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(44.dp).clip(RoundedCornerShape(6.dp)).clickable { onBack() }, contentAlignment = Alignment.Center) {
                GlyphIcon("back", pal.acc, 24.dp, 2.2f)
            }
            Column(Modifier.padding(start = 8.dp)) {
                Txt(title.uppercase(), size = 17f, bold = true, spacing = 1.2f)
                Txt(subtitle, size = 12f, color = pal.sub, maxLines = 1)
            }
        }
        Box(Modifier.fillMaxWidth().height(1.dp).background(pal.line))
        Box(Modifier.weight(1f).navigationBarsPadding()) { content() }
    }
}

@Composable
private fun GridEditor(store: Store, onClose: () -> Unit) {
    val pal = LocalPal.current
    var cols by remember { mutableStateOf(store.cols) }
    var rows by remember { mutableStateOf(store.rows) }
    var size by remember { mutableStateOf(store.iconSize) }
    FullOverlay("Grid & icon size", "Home screen and drawer", onClose) {
        Column(Modifier.padding(16.dp).verticalScroll(rememberScrollState())) {
            Txt("COLUMNS", size = 12f, bold = true, color = pal.sub, spacing = 1.4f, modifier = Modifier.padding(bottom = 8.dp))
            Segmented(listOf("3", "4", "5"), cols - 3) { cols = it + 3 }
            Txt("ROWS", size = 12f, bold = true, color = pal.sub, spacing = 1.4f, modifier = Modifier.padding(top = 18.dp, bottom = 8.dp))
            Segmented(listOf("4", "5", "6"), rows - 4) { rows = it + 4 }
            Txt("ICON SIZE", size = 12f, bold = true, color = pal.sub, spacing = 1.4f, modifier = Modifier.padding(top = 18.dp, bottom = 8.dp))
            Segmented(listOf("Small", "Medium", "Large"), size) { size = it }
            Box(Modifier.height(24.dp))
            PrimaryButton("Save", Modifier.fillMaxWidth()) { store.setGrid(cols, rows, size); onClose() }
        }
    }
}

@Composable
private fun TerminalPicker(store: Store, repo: AppRepo, onClose: () -> Unit) {
    val pal = LocalPal.current
    FullOverlay("Terminal app", "Opened by the Terminal shortcut", onClose) {
        LazyColumn(Modifier.fillMaxSize()) {
            item {
                Column(Modifier.padding(16.dp)) {
                    Txt(NO_TERMINAL_TEXT, size = 12f, color = pal.sub)
                }
                PickRow("Automatic", "Use the separate DiZien terminal app when installed", store.terminalPkg.isBlank(), null, null) {
                    store.updateTerminalPkg(""); onClose()
                }
            }
            items(repo.apps, key = { it.key }) { a ->
                PickRow(a.label, a.pkg, store.terminalPkg == a.pkg, a, null) { store.updateTerminalPkg(a.pkg); onClose() }
            }
        }
    }
}

@Composable
private fun HiddenPicker(store: Store, repo: AppRepo, onClose: () -> Unit) {
    FullOverlay("Hidden apps", "Tap to hide or show in the drawer", onClose) {
        LazyColumn(Modifier.fillMaxSize()) {
            items(repo.apps, key = { it.key }) { a ->
                val hidden = a.key in store.hidden
                PickRow(a.label, a.pkg, hidden, a, if (hidden) "Hidden" else null) {
                    if (hidden) store.hidden.remove(a.key) else { store.hidden.add(a.key); store.pins.remove(a.key); store.dock.remove(a.key); store.savePins(); store.saveDock() }
                    store.saveHidden()
                }
            }
        }
    }
}

@Composable
private fun PickRow(title: String, sub: String, selected: Boolean, app: AppEntry?, tag: String?, onClick: () -> Unit) {
    val pal = LocalPal.current
    val look = LookState(iconStyle = IconStyle.LINE, themeTint = false)
    Row(Modifier.fillMaxWidth().clickable { onClick() }.padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        if (app != null) ItemIcon(LItem(app.key, app.label, app, null), look, pal, 40f)
        else Box(Modifier.size(40.dp).clip(RoundedCornerShape(8.dp)).background(pal.surf2), contentAlignment = Alignment.Center) { GlyphIcon("term", pal.acc, 22.dp) }
        Column(Modifier.padding(start = 14.dp).weight(1f)) {
            Txt(title, size = 14f, bold = true, maxLines = 1)
            Txt(sub, size = 11f, color = pal.sub, maxLines = 1)
        }
        if (tag != null) Pill(tag, false, Modifier.padding(end = 8.dp))
        if (selected) GlyphIcon("check", pal.acc, 22.dp, 2.4f)
    }
}

private val licenseFiles = listOf(
    "GPL-3.0" to "GPL-3.0.txt",
    "Debian Open Use Logo (LGPL-3.0)" to "LGPL-3.0.txt",
    "Debian Open Use Logo (CC BY-SA 3.0 notice)" to "NOTICE-debian-logo.txt",
    "DejaVu Sans Mono font" to "DejaVu-Fonts-License.txt",
    "Jetpack Compose / AndroidX (Apache-2.0)" to "Apache-2.0.txt",
    "Third-party notices" to "NOTICE.txt",
)

@Composable
private fun LicensesList(onBack: () -> Unit, onPick: (String) -> Unit) {
    val pal = LocalPal.current
    FullOverlay("Licenses", "Bundled with this app", onBack) {
        Column(Modifier.verticalScroll(rememberScrollState()).padding(16.dp)) {
            Panel {
                licenseFiles.forEachIndexed { i, (name, _) ->
                    if (i > 0) RowDivider()
                    SettingRow("info", name, null, onClick = { onPick(name) }) { GlyphIcon("chev", pal.sub, 20.dp) }
                }
            }
        }
    }
}

@Composable
private fun LicenseText(name: String, onBack: () -> Unit) {
    val ctx = LocalContext.current
    val pal = LocalPal.current
    val file = licenseFiles.firstOrNull { it.first == name }?.second ?: "NOTICE.txt"
    val text = remember(file) {
        try { ctx.assets.open("licenses/$file").bufferedReader().use { it.readText() } } catch (_: Exception) { "License text not found." }
    }
    FullOverlay(name, "Full text", onBack) {
        Column(Modifier.verticalScroll(rememberScrollState()).padding(16.dp)) {
            androidx.compose.foundation.text.selection.SelectionContainer { Txt(text, size = 11f, color = pal.text) }
        }
    }
}

@Composable
private fun NotifAccessInfo(granted: Boolean, onClose: () -> Unit) {
    val pal = LocalPal.current
    val ctx = LocalContext.current
    FullOverlay("Notification access", if (granted) "Currently on" else "Currently off", onClose) {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Txt("What this does", size = 15f, bold = true)
            Txt(
                "Android does not let a launcher restyle its own notification shade. Instead, DiZien Launcher can show your notifications in its own Amber Slate panel when you swipe down on Home.",
                size = 12.5f, color = pal.text,
            )
            Txt("What you allow", size = 15f, bold = true, modifier = Modifier.padding(top = 6.dp))
            Txt(
                "Android calls this permission \"Notification access\". It lets DiZien Launcher read the title and text of notifications that are showing, open them, and dismiss them from the panel.",
                size = 12.5f, color = pal.text,
            )
            Panel {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Txt("• Notifications are held in memory only to show them here and are never saved.", size = 12f, color = pal.sub)
                    Txt("• This app has no internet permission, so nothing can leave your phone.", size = 12f, color = pal.sub)
                    Txt("• You can turn it off any time in Android settings.", size = 12f, color = pal.sub)
                    Txt("• The stock Android shade stays available: choose it under \"Swipe down on Home\", or use the \"Android shade\" button in the panel.", size = 12f, color = pal.sub)
                }
            }
            Txt(
                "On some phones, apps installed from outside the Play Store show the switch greyed out. If so, open Android Settings > Apps > DiZien Launcher, tap the three-dot menu and choose \"Allow restricted settings\", then try again.",
                size = 11.5f, color = pal.sub,
            )
            Box(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).background(pal.acc)
                    .clickable { openNotificationAccessSettings(ctx) }.padding(vertical = 13.dp),
                contentAlignment = Alignment.Center,
            ) { Txt(if (granted) "Open Android notification access" else "Continue to Android settings", size = 13.5f, bold = true, color = pal.accFg) }
        }
    }
}

@Composable
private fun PagesEditor(store: Store, onClose: () -> Unit) {
    val pal = LocalPal.current
    val ctx = LocalContext.current
    FullOverlay("Home pages", "Swipe left/right on Home to change page", onClose) {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
            Txt("NUMBER OF PAGES", size = 12f, bold = true, color = pal.sub, spacing = 1.4f, modifier = Modifier.padding(bottom = 8.dp))
            Segmented(listOf("1", "2", "3", "4", "5", "6"), (store.currentPages() - 1).coerceIn(0, 5)) { i ->
                store.changeMinPages(i + 1)
                if (store.currentPages() > i + 1) android.widget.Toast.makeText(ctx, "Pages that still hold apps are kept. Move the apps first.", android.widget.Toast.LENGTH_LONG).show()
            }
            Txt("Now: ${store.currentPages()} page(s). Empty pages stay so you can swipe to them and drop icons there.", size = 12.5f, color = pal.sub, modifier = Modifier.padding(top = 12.dp))
            Box(Modifier.height(20.dp))
            PrimaryButton("Add page", Modifier.fillMaxWidth()) {
                if (!store.addPage()) android.widget.Toast.makeText(ctx, "Six pages is the maximum.", android.widget.Toast.LENGTH_SHORT).show()
            }
            Box(Modifier.height(10.dp))
            GhostButton("Remove empty page", Modifier.fillMaxWidth()) {
                if (!store.removeEmptyPage()) android.widget.Toast.makeText(ctx, "No empty page to remove.", android.widget.Toast.LENGTH_SHORT).show()
            }
            Txt("Tip: hold an icon on Home and drag it to the left or right edge to move it to another page.", size = 12.5f, color = pal.sub, modifier = Modifier.padding(top = 18.dp))
        }
    }
}

@Composable
private fun WidgetPagesEditor(store: Store, onClose: () -> Unit) {
    val pal = LocalPal.current
    val ctx = LocalContext.current
    FullOverlay("Widget pages", "Android widgets, left of your Home page", onClose) {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
            Txt("WIDGET PAGES", size = 12f, bold = true, color = pal.sub, spacing = 1.4f, modifier = Modifier.padding(bottom = 8.dp))
            Segmented(listOf("0", "1", "2", "3"), store.widgetPages.coerceIn(0, 3)) { i ->
                if (!store.setWidgetPages(i)) android.widget.Toast.makeText(ctx, "Remove the widgets on that page first.", android.widget.Toast.LENGTH_LONG).show()
            }
            Txt(
                "Widget pages sit to the left of your Home page. Swipe right on Home to reach them, then tap \"Add widget\". Widgets are small views drawn by their own apps (clock, calendar, weather and so on). DiZien Launcher only gives them a place to show.",
                size = 12.5f, color = pal.sub, modifier = Modifier.padding(top = 12.dp),
            )
            Txt(
                "On a widget, tap the small gear to change its height, move it up or down, or remove it. Some widgets ask Android for permission to be added; that is normal.",
                size = 12.5f, color = pal.sub, modifier = Modifier.padding(top = 10.dp),
            )
            Txt("Now: ${store.widgets.size} widget(s) on ${store.widgetPages} page(s).", size = 12.5f, color = pal.text, modifier = Modifier.padding(top = 14.dp))
        }
    }
}

@Composable
private fun LockSetup(store: Store, ready: Boolean, onClose: () -> Unit) {
    val pal = LocalPal.current
    val ctx = LocalContext.current
    val a11y = ScreenLock.a11yEnabled(ctx)
    val admin = ScreenLock.adminActive(ctx)
    FullOverlay("Double-tap to lock", if (ready) "A lock method is on" else "Needs one permission", onClose) {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Panel {
                SettingRow("lock", "Double-tap to lock", "Double-tap an empty spot on Home") {
                    Toggle(store.gDoubleTap) { store.updateGDoubleTap(it) }
                }
            }
            Txt("Android has no permission for apps to lock the screen directly, so you choose one of two methods. Use whichever you prefer; you only need one.", size = 12.5f, color = pal.text)
            Txt("Method 1: Accessibility (Android 9 and newer, recommended)", size = 14.5f, bold = true, modifier = Modifier.padding(top = 4.dp))
            Txt(
                "Turn on \"DiZien screen lock\" in Android Accessibility settings. Android shows a general warning for every accessibility service. This one is set up to receive no events and cannot read your screen; the only thing it does is press Android's own \"lock screen\" action. Fingerprint and face unlock keep working.",
                size = 12.5f, color = pal.sub,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Pill(if (a11y) "On" else "Off", a11y)
                if (android.os.Build.VERSION.SDK_INT < 28) Txt("Needs Android 9+", size = 12f, color = pal.sub)
            }
            PrimaryButton("Open Accessibility settings", Modifier.fillMaxWidth()) {
                try { ctx.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
                catch (_: Exception) { toast(ctx, "Open Android Settings > Accessibility") }
            }
            Txt("Method 2: Device admin (works on Android 7 and newer)", size = 14.5f, bold = true, modifier = Modifier.padding(top = 8.dp))
            Txt(
                "Gives DiZien Launcher the single \"lock the screen\" admin right. It cannot wipe data, change your PIN or see anything. On many phones the first unlock after this kind of lock needs your PIN or pattern instead of fingerprint. Turn the admin off here before uninstalling the launcher.",
                size = 12.5f, color = pal.sub,
            )
            Pill(if (admin) "On" else "Off", admin)
            if (!admin) PrimaryButton("Turn on device admin", Modifier.fillMaxWidth()) {
                try { ctx.startActivity(ScreenLock.adminIntent(ctx)) } catch (_: Exception) { toast(ctx, "Not available on this phone") }
            } else GhostButton("Turn off device admin", Modifier.fillMaxWidth()) { ScreenLock.removeAdmin(ctx); toast(ctx, "Device admin removed") }
            GhostButton("Test: lock now", Modifier.fillMaxWidth()) { if (!ScreenLock.lock(ctx)) toast(ctx, "Turn on a method above first") }
            Txt("Nothing here uses the internet. You can switch both off at any time in Android settings.", size = 11.5f, color = pal.sub)
        }
    }
}

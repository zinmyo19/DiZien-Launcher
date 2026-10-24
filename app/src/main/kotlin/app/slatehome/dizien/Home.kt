package app.slatehome.dizien

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class SheetModel(val title: String, val subtitle: String?, val actions: List<SheetAction>)

/** Everything the home UI needs to do that touches the system. Implemented by [HomeActivity]. */
interface HomeHost {
    fun launch(item: LItem)
    fun expandNotifications()
    fun openLook()
    fun openSettings()
    fun requestDefaultHome()
    fun openWebSearch(q: String)
    fun chooseTerminal()
    fun addWidget(info: android.appwidget.AppWidgetProviderInfo, page: Int)
    fun removeWidget(id: Int)
    fun lockScreen()
}

internal fun itemSizeDp(store: Store) = when (store.iconSize) { 0 -> 48f; 2 -> 64f; else -> 56f }

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun AppCell(
    item: LItem, look: LookState, pal: Palette, iconDp: Float, label: Boolean,
    onClick: () -> Unit, onLongClick: () -> Unit, modifier: Modifier = Modifier,
) {
    Column(
        modifier.combinedClickable(onClick = onClick, onLongClick = onLongClick).padding(vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        ItemIcon(item, look, pal, iconDp)
        if (label) Txt(
            item.label, size = 11f, maxLines = 1, align = TextAlign.Center,
            modifier = Modifier.padding(top = 5.dp, start = 2.dp, end = 2.dp).fillMaxWidth(),
            color = pal.text,
        )
    }
}

@Composable
fun HomeScreen(store: Store, repo: AppRepo, host: HomeHost, wallVersion: Long, resumeTick: Long) {
    val pal = paletteById(store.look.themeId)
    val look = store.look
    val ctx = LocalContext.current
    val ow = if (pal.light) Color.White else pal.text // text drawn straight over the wallpaper

    var sheet by remember { mutableStateOf<SheetModel?>(null) }
    var drawerOpen by remember { mutableStateOf(false) }
    var drawerFocus by remember { mutableStateOf(false) }
    var panelOpen by remember { mutableStateOf(false) }
    var pickerPage by remember { mutableStateOf<Int?>(null) }
    var lastEmptyTap by remember { mutableLongStateOf(0L) }
    var showWelcome by remember { mutableStateOf(!store.setupDone && !isDefaultHome(ctx)) }

    // Clock
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    DisposableEffect(Unit) {
        val r = object : android.content.BroadcastReceiver() {
            override fun onReceive(c: Context?, i: Intent?) { now = System.currentTimeMillis() }
        }
        val f = IntentFilter().apply {
            addAction(Intent.ACTION_TIME_TICK); addAction(Intent.ACTION_TIME_CHANGED); addAction(Intent.ACTION_TIMEZONE_CHANGED)
        }
        ctx.registerReceiver(r, f)
        onDispose { ctx.unregisterReceiver(r) }
    }
    LaunchedEffect(resumeTick) { now = System.currentTimeMillis() }
    var battery by remember { mutableStateOf(-1) }
    DisposableEffect(Unit) {
        val r = object : android.content.BroadcastReceiver() {
            override fun onReceive(c: Context?, i: Intent?) {
                val l = i?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
                val s = i?.getIntExtra(BatteryManager.EXTRA_SCALE, 100) ?: 100
                if (l >= 0 && s > 0) battery = l * 100 / s
            }
        }
        val sticky = ctx.registerReceiver(r, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        sticky?.let { r.onReceive(ctx, it) }
        onDispose { ctx.unregisterReceiver(r) }
    }

    LaunchedEffect(repo.loaded, repo.apps.size) { if (repo.loaded) repo.seedDefaults(store) }
    LaunchedEffect(resumeTick) { drawerOpen = false; sheet = null; panelOpen = false; pickerPage = null; NotifState.ensureBound(ctx) }

    SystemBarIcons(pal.light && (panelOpen || drawerOpen), false)
    val compact = androidx.compose.ui.platform.LocalConfiguration.current.screenHeightDp < 720
    val use24 = android.text.format.DateFormat.is24HourFormat(ctx)
    val timeStr = remember(now, use24) { SimpleDateFormat(if (use24) "HH:mm" else "hh:mm", Locale.getDefault()).format(Date(now)) }
    val dateStr = remember(now) { SimpleDateFormat("EEEE · d MMMM", Locale.getDefault()).format(Date(now)).uppercase() }

    val visibleApps = remember(repo.apps, store.hidden.toList()) { repo.apps.filter { it.key !in store.hidden } }
    val allItems = remember(visibleApps) {
        (Virtual.all.map { it } + visibleApps.map { LItem(it.key, it.label, it, null) }).sortedBy { it.label.lowercase() }
    }

    fun itemActions(it: LItem, where: String): List<SheetAction> {
        val l = ArrayList<SheetAction>()
        val pinned = it.key in store.pins
        val docked = it.key in store.dock
        if (where == "home") l += SheetAction("trash", "Remove from home") { store.pins.remove(it.key); store.savePins() }
        if (where == "dock") l += SheetAction("trash", "Remove from dock") { store.dock.remove(it.key); store.saveDock() }
        if (where == "drawer" && !pinned) l += SheetAction("plus", "Add to home screen") { store.pins.add(it.key); store.savePins(); toast(ctx, "Added to home") }
        if (!docked && where != "dock") {
            if (store.dock.size < 5) l += SheetAction("dock", "Add to dock") { store.dock.add(it.key); store.saveDock(); toast(ctx, "Added to dock") }
        }
        if (it.app != null) {
            l += SheetAction("info", "App info") { repo.appInfo(it.app) }
            if (where == "drawer") l += SheetAction("eye", "Hide from drawer") { store.hidden.add(it.key); store.saveHidden(); store.pins.remove(it.key); store.dock.remove(it.key); store.savePins(); store.saveDock() }
            if (!it.app.isSystem) l += SheetAction("trash", "Uninstall", danger = true) { repo.uninstall(it.app) }
        }
        return l
    }
    fun openItemSheet(it: LItem, where: String) {
        sheet = SheetModel(it.label, it.app?.pkg, itemActions(it, where))
    }
    fun launch(it: LItem) {
        drawerOpen = false
        host.launch(it)
    }
    fun emptyTap() {
        if (!store.gDoubleTap || drawerOpen || panelOpen || sheet != null || pickerPage != null) return
        val t = System.currentTimeMillis()
        if (t - lastEmptyTap < 380L) { lastEmptyTap = 0L; host.lockScreen() } else lastEmptyTap = t
    }
    fun widgetMenu(w: WidgetRec) {
        val l = ArrayList<SheetAction>()
        for ((n, h) in Widgets.SIZES) l += SheetAction("grid", "Height: $n${if (w.h == h) "  ✓" else ""}") { store.resizeWidget(w.id, h) }
        l += SheetAction("chev", "Move up") { store.moveWidget(w.id, -1) }
        l += SheetAction("chev", "Move down") { store.moveWidget(w.id, 1) }
        l += SheetAction("trash", "Remove widget", danger = true) { host.removeWidget(w.id) }
        sheet = SheetModel("Widget", "Resize, move or remove", l)
    }

    Box(
        Modifier.fillMaxSize()
            .pointerInput(store.gDoubleTap) {
                detectTapGestures(onTap = { emptyTap() })
            }
            .pointerInput(store.swipeMode, store.gSwipeUp) {
                var total = 0f
                detectVerticalDragGestures(
                    onDragStart = { total = 0f },
                    onDragEnd = {
                        if (total < -90f) { if (!panelOpen && store.gSwipeUp) { drawerFocus = false; drawerOpen = true } }
                        else if (total > 120f && !drawerOpen && !panelOpen) {
                            when (store.swipeMode) { 0 -> panelOpen = true; 1 -> host.expandNotifications(); 3 -> { drawerFocus = true; drawerOpen = true } }
                        }
                    },
                    onDragCancel = { total = 0f },
                    onVerticalDrag = { _, dy -> total += dy },
                )
            },
    ) {
        WallpaperBackground(look.wallpaper, Modifier.fillMaxSize(), wallVersion, publishGlass = true)
        // Subtle scrim keeps icon labels legible over bright wallpapers.
        Box(Modifier.fillMaxSize().background(Color(0x22000000)))

        // Soft Amber Slate fade behind the status bar so its icons stay readable on any wallpaper.
        Box(Modifier.fillMaxWidth().height(56.dp).background(androidx.compose.ui.graphics.Brush.verticalGradient(listOf(if (pal.light) Color(0x99000000) else pal.bg.copy(alpha = 0.7f), Color.Transparent))))
        Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
            // Clock block
            if (store.showDockClock) {
                Column(Modifier.padding(start = 24.dp, end = 24.dp, top = if (compact) 6.dp else 18.dp)) {
                    Txt(timeStr, size = if (compact) 48f else 60f, bold = true, color = ow)
                    Txt(dateStr, size = 14f, color = ow.copy(alpha = 0.85f), spacing = 1.2f)
                    Row(
                        Modifier.padding(top = if (compact) 8.dp else 12.dp)
                            .glass(RoundedCornerShape(50), pal, pal.bg.copy(alpha = 0.55f), 0.7f).padding(4.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Row(
                            Modifier.clip(RoundedCornerShape(50)).background(pal.acc).padding(horizontal = 12.dp, vertical = 5.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Box(Modifier.size(7.dp).clip(CircleShape).background(pal.accFg))
                            Txt(pal.name, size = 11.5f, bold = true, color = pal.accFg, modifier = Modifier.padding(start = 7.dp))
                        }
                        if (NotifState.items.isEmpty()) Pill("${allItems.size} apps", false)
                        if (battery >= 0) Pill("$battery%", false)
                        if (NotifState.items.isNotEmpty()) Pill("${NotifState.items.size} new", true)
                    }
                }
            }

            // Search bar
            Row(
                Modifier.padding(horizontal = 16.dp, vertical = if (compact) 10.dp else 14.dp).fillMaxWidth().height(if (compact) 44.dp else 50.dp)
                    .glass(RoundedCornerShape(if (pal.glass) 16.dp else 6.dp), pal, pal.bg.copy(alpha = 0.78f))
                    .border(1.dp, pal.line, RoundedCornerShape(if (pal.glass) 16.dp else 6.dp))
                    .clickable { drawerFocus = true; drawerOpen = true }
                    .padding(horizontal = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                GlyphIcon("search", pal.sub, 20.dp)
                Txt("Search apps…", size = 13.5f, color = pal.sub, modifier = Modifier.padding(start = 12.dp).weight(1f))
                Txt("all", size = 12.5f, color = pal.acc, bold = true)
            }

            // Grid area: swipe left/right between pages, long-press to drag
            HomeGrid(
                store, repo, look, pal, resumeTick, ow,
                onLaunch = { launch(it) },
                onItemMenu = { openItemSheet(it, "home") },
                onEmptyMenu = {
                    when (store.gLongPress) {
                        1 -> host.openLook()
                        2 -> {}
                        else -> sheet = SheetModel("Home screen", null, listOf(
                            SheetAction("palette", "Change look & wallpaper") { host.openLook() },
                            SheetAction("grid", "Add apps to home") { drawerFocus = false; drawerOpen = true },
                            SheetAction("plus", "Add a widget") { store.ensureWidgetPage(); pickerPage = 0 },
                            SheetAction("gear", "Launcher settings") { host.openSettings() },
                        ))
                    }
                },
                onEmptyTap = { emptyTap() },
                onAddWidget = { pickerPage = it },
                onWidgetMenu = { widgetMenu(it) },
                modifier = Modifier.weight(1f).fillMaxWidth().padding(horizontal = 10.dp),
            )
            // Swipe-up hint
            Txt(
                (if (store.gSwipeUp) "▲ SWIPE UP · ALL APPS (${allItems.size})" else "▲ TAP · ALL APPS (${allItems.size})"), size = 10.5f, spacing = 1.4f, color = ow.copy(alpha = 0.8f),
                align = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp).clickable { drawerFocus = false; drawerOpen = true },
            )

            // Dock
            Row(
                Modifier.padding(horizontal = 16.dp).padding(bottom = 10.dp).fillMaxWidth().height(84.dp)
                    .glass(RoundedCornerShape(if (pal.glass) 26.dp else 14.dp), pal, pal.bg.copy(alpha = 0.82f))
                    .border(1.dp, pal.line, RoundedCornerShape(if (pal.glass) 26.dp else 14.dp)).padding(horizontal = 8.dp),
                horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically,
            ) {
                val dockItems = store.dock.mapNotNull { repo.find(it) }
                if (dockItems.isEmpty()) {
                    Txt("Dock is empty · long-press an app in the drawer to add it", size = 11.5f, color = pal.sub, align = TextAlign.Center)
                }
                dockItems.forEach {
                    AppCell(it, look, pal, 54f, false, onClick = { launch(it) }, onLongClick = { openItemSheet(it, "dock") })
                }
            }
        }

        // All-apps drawer
        AnimatedVisibility(
            drawerOpen,
            enter = slideInVertically(initialOffsetY = { it / 3 }) + fadeIn(),
            exit = slideOutVertically(targetOffsetY = { it / 3 }) + fadeOut(),
        ) {
            Drawer(
                items = allItems, store = store, pal = pal, look = look, focusSearch = drawerFocus, host = host,
                onLaunch = { launch(it) }, onLong = { openItemSheet(it, "drawer") }, onClose = { drawerOpen = false },
            )
        }

        NotificationPanel(
            panelOpen, pal, timeStr, dateStr,
            onClose = { panelOpen = false },
            onAndroidShade = { panelOpen = false; host.expandNotifications() },
            onLauncherSettings = { panelOpen = false; host.openSettings() },
        )
        pickerPage?.let { pg -> WidgetPicker(pal, onPick = { host.addWidget(it, pg); pickerPage = null }, onClose = { pickerPage = null }) }
        sheet?.let { s -> ActionSheet(s.title, s.subtitle, s.actions) { sheet = null } }

        if (showWelcome && sheet == null && !drawerOpen) {
            ActionSheet(
                "Welcome to DiZien Launcher",
                "Make it your home app so the Home button opens this screen. You can switch back any time in Android settings.",
                listOf(
                    SheetAction("home", "Set as default home app") { store.markSetupDone(); showWelcome = false; host.requestDefaultHome() },
                    SheetAction("back", "Not now") { store.markSetupDone(); showWelcome = false },
                ),
            ) { store.markSetupDone(); showWelcome = false }
        }
    }

    BackHandler(enabled = drawerOpen || sheet != null || panelOpen || pickerPage != null) {
        if (sheet != null) sheet = null else if (pickerPage != null) pickerPage = null else if (panelOpen) panelOpen = false else drawerOpen = false
    }
}

@Composable
fun Drawer(
    items: List<LItem>, store: Store, pal: Palette, look: LookState, focusSearch: Boolean, host: HomeHost,
    onLaunch: (LItem) -> Unit, onLong: (LItem) -> Unit, onClose: () -> Unit,
) {
    var q by remember { mutableStateOf("") }
    val fr = remember { FocusRequester() }
    LaunchedEffect(focusSearch) { if (focusSearch) { try { fr.requestFocus() } catch (_: Exception) { } } }
    val shown = remember(q, items) {
        val t = q.trim()
        if (t.isEmpty()) items
        else items.filter { it.label.contains(t, true) }.sortedBy { !it.label.startsWith(t, true) }
    }
    Column(
        Modifier.fillMaxSize().glass(androidx.compose.ui.graphics.RectangleShape, pal, pal.bg, 1.25f).statusBarsPadding().navigationBarsPadding()
            .pointerInput(Unit) {
                var total = 0f
                detectVerticalDragGestures(
                    onDragStart = { total = 0f },
                    onDragEnd = { if (total > 160f) onClose() },
                    onVerticalDrag = { _, dy -> total += dy },
                )
            },
    ) {
        Row(
            Modifier.padding(16.dp).fillMaxWidth().height(50.dp).glass(RoundedCornerShape(6.dp), pal, pal.surf, 0.7f)
                .border(1.dp, pal.acc, RoundedCornerShape(6.dp)).padding(horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            GlyphIcon("search", pal.acc, 20.dp)
            Box(Modifier.padding(start = 12.dp).weight(1f)) {
                if (q.isEmpty()) Txt("Search apps…", size = 13.5f, color = pal.sub)
                BasicTextField(
                    q, { q = it }, singleLine = true,
                    textStyle = TextStyle(fontFamily = Mono, fontSize = 14.sp, color = pal.text),
                    cursorBrush = SolidColor(pal.acc),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go),
                    keyboardActions = KeyboardActions(onGo = { val f = shown.firstOrNull(); if (f != null) onLaunch(f) else if (q.isNotBlank()) host.openWebSearch(q.trim()) }),
                    modifier = Modifier.fillMaxWidth().focusRequester(fr),
                )
            }
            if (q.isNotEmpty()) Txt("clear", size = 12.5f, color = pal.acc, bold = true, modifier = Modifier.clickable { q = "" }.padding(4.dp))
        }
        if (q.isNotBlank()) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(bottom = 8.dp)
                    .clip(RoundedCornerShape(6.dp)).glass(RoundedCornerShape(6.dp), pal, pal.surf, 0.7f)
                    .border(1.dp, pal.line, RoundedCornerShape(6.dp))
                    .clickable { host.openWebSearch(q.trim()) }.padding(14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                GlyphIcon("globe", pal.acc, 20.dp)
                Txt("Search the web for “${q.trim()}”", size = 13f, modifier = Modifier.padding(start = 12.dp).weight(1f), maxLines = 1)
            }
        }
        Txt(
            if (q.isBlank()) "ALL APPS (${items.size})" else "${shown.size} MATCH${if (shown.size == 1) "" else "ES"}",
            size = 11.5f, bold = true, spacing = 1.4f, color = pal.sub, modifier = Modifier.padding(start = 20.dp, bottom = 6.dp),
        )
        LazyVerticalGrid(
            GridCells.Fixed(store.cols), Modifier.weight(1f).padding(horizontal = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(shown, key = { it.key }) { it ->
                AppCell(it, look, pal, itemSizeDp(store), true, onClick = { onLaunch(it) }, onLongClick = { onLong(it) })
            }
        }
    }
    BackHandler { onClose() }
}

package app.slatehome.dizien

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.snapshots.SnapshotStateList

object IconStyle {
    const val LINE = 0; const val SOLID = 1; const val ROUND = 2; const val SOFT = 3; const val GRAD = 4
    const val RING = 5; const val HEX = 6; const val LEAF = 7; const val BARE = 8
    const val GLASS = 9
    const val COUNT = 10
}

/** The look that is applied to the launcher (committed) or being edited on the Look screen (draft). */
data class LookState(
    val themeId: String = "amber_slate",
    val iconStyle: Int = IconStyle.LINE,
    val themeTint: Boolean = true,
    val wallpaper: Int = 0,
)

const val WALL_CUSTOM = 100

/** One Android widget placed on a widget page: [page] is the widget-page number (0-based), [h] its height in dp. */
data class WidgetRec(val page: Int, val id: Int, val h: Int)

/** Simple SharedPreferences-backed, Compose-observable settings. Everything stays on the device. */
class Store private constructor(ctx: Context) {
    private val sp: SharedPreferences = ctx.getSharedPreferences("dizien", Context.MODE_PRIVATE)

    var look by mutableStateOf(
        LookState(
            themeId = sp.getString("theme", "amber_slate")!!,
            iconStyle = sp.getInt("iconStyle", IconStyle.LINE),
            themeTint = sp.getBoolean("themeTint", true),
            wallpaper = sp.getInt("wallpaper", 0),
        )
    )
        private set

    var cols by mutableStateOf(sp.getInt("cols", 4)); private set
    var rows by mutableStateOf(sp.getInt("rows", 5)); private set
    var iconSize by mutableStateOf(sp.getInt("iconSize", 1)); private set // 0 small, 1 medium, 2 large
    var showLabels by mutableStateOf(sp.getBoolean("labels", true)); private set
    var showDockClock by mutableStateOf(sp.getBoolean("dockClock", true)); private set
    /** Swipe down on Home: 0 = DiZien panel, 1 = Android's own shade, 2 = off. */
    var swipeMode by mutableStateOf(if (sp.contains("swipeMode")) sp.getInt("swipeMode", 0) else if (sp.getBoolean("swipeDown", true)) 0 else 2); private set
    var terminalPkg by mutableStateOf(sp.getString("terminalPkg", "") ?: ""); private set
    var setupDone by mutableStateOf(sp.getBoolean("setupDone", false)); private set
    var layoutSeeded by mutableStateOf(sp.getBoolean("seeded", false)); private set
    var alsoSystemWallpaper by mutableStateOf(sp.getBoolean("sysWall", false)); private set

    /** Gestures. Swipe down uses [swipeMode]: 0 = DiZien panel, 1 = Android shade, 2 = off, 3 = search apps. */
    var gSwipeUp by mutableStateOf(sp.getBoolean("gSwipeUp", true)); private set
    var gDoubleTap by mutableStateOf(sp.getBoolean("gDoubleTap", false)); private set
    /** Long-press on empty Home space: 0 = menu, 1 = open Look, 2 = off. */
    var gLongPress by mutableStateOf(sp.getInt("gLongPress", 0)); private set
    fun updateGSwipeUp(v: Boolean) { gSwipeUp = v; sp.edit().putBoolean("gSwipeUp", v).apply() }
    fun updateGDoubleTap(v: Boolean) { gDoubleTap = v; sp.edit().putBoolean("gDoubleTap", v).apply() }
    fun updateGLongPress(v: Int) { gLongPress = v; sp.edit().putInt("gLongPress", v).apply() }
    /** Widget pages sit to the left of the app pages. */
    var widgetPages by mutableStateOf(sp.getInt("widgetPages", 1).coerceIn(0, 3)); private set
    val widgets: SnapshotStateList<WidgetRec> = mutableStateListOf<WidgetRec>().also { l ->
        for (s in readList("widgets")) {
            val p = s.split(',')
            val a = p.getOrNull(0)?.toIntOrNull(); val b = p.getOrNull(1)?.toIntOrNull(); val c = p.getOrNull(2)?.toIntOrNull()
            if (a != null && b != null && c != null) l.add(WidgetRec(a, b, c))
        }
    }
    fun saveWidgets() = saveList("widgets", widgets.map { "${it.page},${it.id},${it.h}" })
    fun addWidget(page: Int, id: Int, h: Int) { widgets.add(WidgetRec(page, id, h)); saveWidgets() }
    fun removeWidget(id: Int) { widgets.removeAll { it.id == id }; saveWidgets() }
    fun resizeWidget(id: Int, h: Int) { val i = widgets.indexOfFirst { it.id == id }; if (i >= 0) { widgets[i] = widgets[i].copy(h = h); saveWidgets() } }
    /** Moves a widget up (-1) or down (+1) among the widgets on its page. */
    fun moveWidget(id: Int, dir: Int) {
        val i = widgets.indexOfFirst { it.id == id }; if (i < 0) return
        val pg = widgets[i].page
        var j = i + dir
        while (j in widgets.indices && widgets[j].page != pg) j += dir
        if (j !in widgets.indices) return
        val t = widgets[i]; widgets[i] = widgets[j]; widgets[j] = t; saveWidgets()
    }
    /** Returns false when a widget page that would disappear still holds widgets. */
    fun setWidgetPages(n: Int): Boolean {
        val m = n.coerceIn(0, 3)
        if (widgets.any { it.page >= m }) return false
        widgetPages = m; sp.edit().putInt("widgetPages", m).apply(); return true
    }
    fun ensureWidgetPage() { if (widgetPages == 0) setWidgetPages(1) }
    val pins: SnapshotStateList<String> = mutableStateListOf<String>().also { it.addAll(readList("pins")) }
    val dock: SnapshotStateList<String> = mutableStateListOf<String>().also { it.addAll(readList("dock")) }
    val hidden: SnapshotStateList<String> = mutableStateListOf<String>().also { it.addAll(readList("hidden")) }

    /** Home layout: one entry per slot, pages of equal size laid out one after another; "-" is an empty slot. */
    val slots: SnapshotStateList<String> = mutableStateListOf<String>().also { it.addAll(readList("slots")) }
    private var slotPageSize = sp.getInt("slotPageSize", 0)
    /** Minimum number of Home pages (1-6). Starts at 2 so the swipe gesture is discoverable straight away. */
    var minPages by mutableStateOf(sp.getInt("minPages", 2).coerceIn(1, 6)); private set
    var pagesHintSeen by mutableStateOf(sp.getBoolean("pagesHint", false)); private set
    fun markPagesHint() { pagesHintSeen = true; sp.edit().putBoolean("pagesHint", true).apply() }
    /** Pages that exist right now (pages with apps, plus empty ones kept by [minPages]). */
    fun currentPages(): Int = if (slotPageSize > 0) maxOf(1, slots.size / slotPageSize) else minPages
    fun changeMinPages(n: Int) {
        minPages = n.coerceIn(1, 6); sp.edit().putInt("minPages", minPages).apply(); applyPageCount()
    }
    private fun applyPageCount() {
        val p = slotPageSize; if (p <= 0) return
        val list = slots.toMutableList()
        while (list.size < p * minPages) list.add("-")
        while (list.size > p && list.size / p > minPages && list.takeLast(p).all { it == "-" }) repeat(p) { list.removeAt(list.size - 1) }
        if (list != slots.toList()) { slots.clear(); slots.addAll(list) }
        saveSlots(p)
    }
    fun addPage(): Boolean {
        val cur = currentPages(); if (cur >= 6) return false
        minPages = cur + 1; sp.edit().putInt("minPages", minPages).apply(); applyPageCount(); return true
    }
    /** Removes one empty page (the last empty one). Returns false when every page still holds apps. */
    fun removeEmptyPage(): Boolean {
        val p = slotPageSize; val cur = currentPages()
        if (cur <= 1) return false
        if (p <= 0) { changeMinPages(cur - 1); return true }
        val empty = (cur - 1 downTo 0).firstOrNull { pg -> (pg * p until (pg + 1) * p).all { slots[it] == "-" } } ?: return false
        repeat(p) { slots.removeAt(empty * p) }
        minPages = (cur - 1).coerceAtLeast(1); sp.edit().putInt("minPages", minPages).apply(); saveSlots(p)
        return true
    }
    private fun readList(k: String): List<String> =
        (sp.getString(k, "") ?: "").split('\n').filter { it.isNotBlank() }

    private fun saveList(k: String, l: List<String>) { sp.edit().putString(k, l.joinToString("\n")).apply() }
    fun savePins() = saveList("pins", pins)
    fun saveDock() = saveList("dock", dock)
    fun saveHidden() = saveList("hidden", hidden)
    fun saveSlots(p: Int) { saveList("slots", slots); slotPageSize = p; sp.edit().putInt("slotPageSize", p).apply() }

    /** Drops empty pages at the end (always keeps one page). */
    fun trimSlots(p: Int) {
        while (slots.size > p && slots.size / p > minPages && slots.takeLast(p).all { it == "-" }) repeat(p) { slots.removeAt(slots.size - 1) }
    }

    /**
     * Keeps [slots] in step with [pins]: removed apps leave an empty hole (nothing shifts), new apps take the
     * first free slot, and the layout is only re-packed if the number of slots per page changes.
     */
    fun reconcileSlots(p: Int) {
        var list = slots.toMutableList()
        if (list.isNotEmpty() && slotPageSize != p) list = list.filter { it != "-" }.toMutableList()
        val seen = HashSet<String>()
        for (i in list.indices) {
            val k = list[i]
            if (k != "-" && (k !in pins || !seen.add(k))) list[i] = "-"
        }
        for (k in pins) if (k !in seen) {
            var i = list.indexOf("-")
            if (i < 0) { repeat(p) { list.add("-") }; i = list.size - p }
            list[i] = k; seen.add(k)
        }
        while (list.size < p * minPages || list.size % p != 0) list.add("-")
        while (list.size > p && list.size / p > minPages && list.takeLast(p).all { it == "-" }) repeat(p) { list.removeAt(list.size - 1) }
        if (list != slots.toList() || slotPageSize != p) {
            slots.clear(); slots.addAll(list); saveSlots(p)
        }
    }

    fun applyLook(l: LookState) {
        look = l
        sp.edit().putString("theme", l.themeId).putInt("iconStyle", l.iconStyle)
            .putBoolean("themeTint", l.themeTint).putInt("wallpaper", l.wallpaper).apply()
    }
    fun applyWallpaper(id: Int, system: Boolean) {
        applyLook(look.copy(wallpaper = id)); alsoSystemWallpaper = system
        sp.edit().putBoolean("sysWall", system).apply()
    }
    fun setGrid(c: Int, r: Int, size: Int) {
        cols = c; rows = r; iconSize = size
        sp.edit().putInt("cols", c).putInt("rows", r).putInt("iconSize", size).apply()
    }
    fun updateShowLabels(v: Boolean) { showLabels = v; sp.edit().putBoolean("labels", v).apply() }
    fun updateShowDockClock(v: Boolean) { showDockClock = v; sp.edit().putBoolean("dockClock", v).apply() }
    fun updateSwipeMode(v: Int) { swipeMode = v; sp.edit().putInt("swipeMode", v).apply() }
    fun updateTerminalPkg(v: String) { terminalPkg = v; sp.edit().putString("terminalPkg", v).apply() }
    fun markSetupDone() { setupDone = true; sp.edit().putBoolean("setupDone", true).apply() }
    fun markSeeded() { layoutSeeded = true; sp.edit().putBoolean("seeded", true).apply() }

    fun resetLayout() {
        pins.clear(); dock.clear(); slots.clear(); savePins(); saveDock(); saveSlots(0)
        layoutSeeded = false; sp.edit().putBoolean("seeded", false).apply()
    }

    companion object {
        @Volatile private var inst: Store? = null
        fun get(ctx: Context): Store =
            inst ?: synchronized(this) { inst ?: Store(ctx.applicationContext).also { inst = it } }
    }
}

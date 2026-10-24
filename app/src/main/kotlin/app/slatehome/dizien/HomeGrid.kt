package app.slatehome.dizien

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

/**
 * Home screen pages: fixed slots, swiped left/right, no vertical scrolling.
 * Tap = open, long-press = menu, long-press + move = drag to another slot or page.
 */
@Composable
fun HomeGrid(
    store: Store, repo: AppRepo, look: LookState, pal: Palette, resumeTick: Long, labelColor: androidx.compose.ui.graphics.Color,
    onLaunch: (LItem) -> Unit, onItemMenu: (LItem) -> Unit, onEmptyMenu: () -> Unit,
    onEmptyTap: () -> Unit = {}, onAddWidget: (Int) -> Unit = {}, onWidgetMenu: (WidgetRec) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current
    val haptic = LocalHapticFeedback.current
    BoxWithConstraints(modifier) {
        val dotsH = 22f
        val labelH = if (store.showLabels) 22f else 0f
        val iconBase = itemSizeDp(store)
        val availH = (maxHeight.value - dotsH).coerceAtLeast(60f)
        val fit = (availH / (iconBase + labelH + 14f)).toInt()
        val rows = minOf(store.rows, fit).coerceAtLeast(1)
        val cols = store.cols.coerceAtLeast(1)
        val p = rows * cols
        val cellH = availH / rows
        val iconDp = minOf(iconBase, cellH - labelH - 14f).coerceAtLeast(32f)
        val wPx = with(density) { maxWidth.toPx() }
        val cellHPx = with(density) { cellH.dp.toPx() }
        val cellWPx = wPx / cols

        LaunchedEffect(store.pins.joinToString("|"), p, store.minPages) { store.reconcileSlots(p) }
        val wp = store.widgetPages
        val iconPages = maxOf(1, (store.slots.size + p - 1) / p)
        val pages = wp + iconPages
        val pager = rememberPagerState(initialPage = wp) { pages }
        LaunchedEffect(resumeTick) { pager.scrollToPage(wp) }
        // Touch handling for icons only runs while an app page (not a widget page) is showing.
        val iconActive = pager.currentPage >= wp

        var dragKey by remember { mutableStateOf<String?>(null) }
        var dragFrom by remember { mutableIntStateOf(-1) }
        var dragStart by remember { mutableStateOf(Offset.Zero) }
        var dragPos by remember { mutableStateOf(Offset.Zero) }
        var moved by remember { mutableStateOf(false) }
        var edge by remember { mutableIntStateOf(0) }

        val curPage by rememberUpdatedState(pager.currentPage - wp)
        val onLaunchU by rememberUpdatedState(onLaunch)
        val onItemMenuU by rememberUpdatedState(onItemMenu)
        val onEmptyMenuU by rememberUpdatedState(onEmptyMenu)

        fun slotAt(o: Offset): Int {
            if (curPage < 0 || o.y < 0f || o.y >= cellHPx * rows || o.x < 0f || o.x >= wPx) return -1
            val c = (o.x / cellWPx).toInt().coerceIn(0, cols - 1)
            val r = (o.y / cellHPx).toInt().coerceIn(0, rows - 1)
            return curPage * p + r * cols + c
        }
        fun itemIn(slot: Int): LItem? {
            val k = store.slots.getOrNull(slot) ?: return null
            return if (k == "-") null else repo.find(k)
        }

        // Turn pages while an icon is held at the left/right edge.
        LaunchedEffect(edge, pager.currentPage, dragKey) {
            if (dragKey == null || edge == 0) return@LaunchedEffect
            delay(550)
            val cur = pager.currentPage
            if (edge < 0 && cur > wp) pager.animateScrollToPage(cur - 1)
            else if (edge > 0) {
                if (cur == pages - 1) {
                    val lastEmpty = store.slots.takeLast(p).all { it == "-" }
                    if (!lastEmpty) { repeat(p) { store.slots.add("-") }; delay(60) }
                }
                if (cur + 1 < wp + maxOf(1, (store.slots.size + p - 1) / p)) pager.animateScrollToPage(cur + 1)
            }
        }

        Column(Modifier.fillMaxSize()) {
            Box(
                Modifier.fillMaxWidth().height(cellH.dp * rows)
                    .pointerInput(p, cols, rows, cellHPx, wPx, iconActive) {
                        if (!iconActive) awaitCancellation()
                        detectTapGestures(onTap = { o -> val it = itemIn(slotAt(o)); if (it != null) onLaunchU(it) else onEmptyTap() })
                    }
                    .pointerInput(p, cols, rows, cellHPx, wPx, iconActive) {
                        if (!iconActive) awaitCancellation()
                        val edgePx = 36.dp.toPx()
                        detectDragGesturesAfterLongPress(
                            onDragStart = { o ->
                                val s = slotAt(o)
                                val it = itemIn(s)
                                if (it != null) {
                                    dragKey = it.key; dragFrom = s; dragStart = o; dragPos = o; moved = false
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                } else { dragKey = null; onEmptyMenuU() }
                            },
                            onDrag = { ch, amt ->
                                ch.consume()
                                if (dragKey != null) {
                                    dragPos += amt
                                    if (!moved && (dragPos - dragStart).getDistance() > 14f) moved = true
                                    edge = if (!moved) 0 else if (dragPos.x < edgePx) -1 else if (dragPos.x > wPx - edgePx) 1 else 0
                                }
                            },
                            onDragEnd = {
                                val k = dragKey
                                edge = 0
                                if (k != null) {
                                    if (!moved) repo.find(k)?.let { onItemMenuU(it) }
                                    else {
                                        val to = slotAt(dragPos)
                                        if (to >= 0 && to != dragFrom && to < store.slots.size && dragFrom < store.slots.size) {
                                            val other = store.slots[to]
                                            store.slots[to] = k
                                            store.slots[dragFrom] = other
                                        }
                                        store.trimSlots(p); store.saveSlots(p)
                                    }
                                }
                                dragKey = null; moved = false
                            },
                            onDragCancel = { edge = 0; dragKey = null; moved = false; store.trimSlots(p); store.saveSlots(p) },
                        )
                    },
            ) {
                HorizontalPager(pager, Modifier.fillMaxSize(), userScrollEnabled = dragKey == null) { pageAll ->
                    if (pageAll < wp) {
                        WidgetPage(store, pal, pageAll, labelColor, onAdd = { onAddWidget(pageAll) }, onMenu = { onWidgetMenu(it) })
                    } else {
                    val page = pageAll - wp
                    Column(Modifier.fillMaxSize()) {
                        for (r in 0 until rows) {
                            Row(Modifier.fillMaxWidth().height(cellH.dp)) {
                                for (c in 0 until cols) {
                                    val slot = page * p + r * cols + c
                                    val it = itemIn(slot)
                                    Box(Modifier.weight(1f).fillMaxHeight(), contentAlignment = Alignment.TopCenter) {
                                        if (it != null) Column(
                                            Modifier.padding(top = 5.dp).alpha(if (moved && it.key == dragKey) 0.25f else 1f),
                                            horizontalAlignment = Alignment.CenterHorizontally,
                                        ) {
                                            ItemIcon(it, look, pal, iconDp)
                                            if (store.showLabels) Txt(
                                                it.label, size = 11f, maxLines = 1, align = TextAlign.Center,
                                                modifier = Modifier.padding(top = 5.dp, start = 2.dp, end = 2.dp).fillMaxWidth(),
                                                color = labelColor,
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                    }
                }
                // First-run hint about pages and dragging
                if (!store.pagesHintSeen) {
                    LaunchedEffect(pager.currentPage) { if (pager.currentPage != wp) store.markPagesHint() }
                    LaunchedEffect(Unit) { delay(12000); store.markPagesHint() }
                    Row(
                        Modifier.align(Alignment.BottomCenter).padding(horizontal = 14.dp, vertical = 6.dp)
                            .clip(RoundedCornerShape(10.dp)).background(pal.bg.copy(alpha = 0.88f))
                            .border(1.dp, pal.acc.copy(alpha = 0.7f), RoundedCornerShape(10.dp))
                            .clickable { store.markPagesHint() }.padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Txt(
                            "Swipe left/right for more pages. Hold an icon and drag it to the edge to move it.",
                            size = 11.5f, color = pal.text, modifier = Modifier.weight(1f),
                        )
                        Txt("OK", size = 12f, bold = true, color = pal.acc, modifier = Modifier.padding(start = 10.dp))
                    }
                }
                // Drop target outline + the icon under the finger
                val dk = dragKey
                if (dk != null && moved) {
                    val to = slotAt(dragPos)
                    if (to >= 0) {
                        val rel = to - curPage * p
                        val tr = rel / cols; val tc = rel % cols
                        Box(
                            Modifier.offset { IntOffset((tc * cellWPx).roundToInt(), (tr * cellHPx).roundToInt()) }
                                .size(width = with(density) { cellWPx.toDp() }, height = cellH.dp)
                                .padding(3.dp).border(1.5.dp, pal.acc.copy(alpha = 0.8f), RoundedCornerShape(10.dp)),
                        )
                    }
                    repo.find(dk)?.let { item ->
                        val half = with(density) { (iconDp / 2f).dp.toPx() }
                        Box(
                            Modifier.offset { IntOffset((dragPos.x - half).roundToInt(), (dragPos.y - half * 1.6f).roundToInt()) }
                                .size(iconDp.dp).scale(1.18f).alpha(0.95f),
                        ) { ItemIcon(item, look, pal, iconDp) }
                    }
                }
            }
            // Page dots
            Row(
                Modifier.fillMaxWidth().height(dotsH.dp), horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                repeat(pages) { i ->
                    val on = i == pager.currentPage
                    Box(
                        Modifier.padding(horizontal = 4.dp).height(8.dp).width(if (on) 20.dp else 8.dp).clip(CircleShape)
                            .background(if (on) pal.acc else labelColor.copy(alpha = 0.4f)),
                    )
                }
            }
        }
    }
}

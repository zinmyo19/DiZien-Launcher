package app.slatehome.dizien

import android.appwidget.AppWidgetHost
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.Drawable
import android.os.Bundle
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Holds the one AppWidgetHost for the launcher. Widgets are drawn by their own apps; this app never touches the network. */
object Widgets {
    private const val HOST_ID = 0x44A1
    private var hostInst: AppWidgetHost? = null
    private var awmInst: AppWidgetManager? = null
    fun host(ctx: Context): AppWidgetHost =
        hostInst ?: AppWidgetHost(ctx.applicationContext, HOST_ID).also { hostInst = it }
    fun awm(ctx: Context): AppWidgetManager =
        awmInst ?: AppWidgetManager.getInstance(ctx.applicationContext).also { awmInst = it }
    fun listen(ctx: Context) { try { host(ctx).startListening() } catch (_: Throwable) { } }
    fun stop(ctx: Context) { try { host(ctx).stopListening() } catch (_: Throwable) { } }
    fun defaultHeightDp(ctx: Context, info: AppWidgetProviderInfo?): Int {
        val d = ctx.resources.displayMetrics.density
        val h = if (info != null && info.minHeight > 0) (info.minHeight / d).toInt() else 140
        return h.coerceIn(100, 360)
    }
    val SIZES = listOf("Small" to 110, "Medium" to 170, "Large" to 250, "Tall" to 360)
}

private fun drawableToBitmap(d: Drawable, maxPx: Int): Bitmap? = try {
    val w0 = d.intrinsicWidth.takeIf { it > 0 } ?: maxPx
    val h0 = d.intrinsicHeight.takeIf { it > 0 } ?: maxPx
    val sc = minOf(1f, maxPx.toFloat() / maxOf(w0, h0))
    val w = (w0 * sc).toInt().coerceAtLeast(1); val h = (h0 * sc).toInt().coerceAtLeast(1)
    val b = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
    d.setBounds(0, 0, w, h); d.draw(Canvas(b)); b
} catch (_: Throwable) { null }

/** A Home page that holds Android widgets stacked top to bottom. */
@Composable
fun WidgetPage(
    store: Store, pal: Palette, page: Int, labelColor: androidx.compose.ui.graphics.Color,
    onAdd: () -> Unit, onMenu: (WidgetRec) -> Unit,
) {
    val ctx = LocalContext.current
    val list = store.widgets.filter { it.page == page }
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val wDp = maxWidth.value.toInt() - 8
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 4.dp, vertical = 6.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            if (list.isEmpty()) {
                Txt("WIDGETS", size = 11.5f, bold = true, spacing = 1.4f, color = labelColor.copy(alpha = 0.85f), modifier = Modifier.padding(start = 6.dp, top = 6.dp))
                Txt(
                    "Put clocks, weather, calendar and other Android widgets here. They are drawn by their own apps.",
                    size = 12f, color = labelColor.copy(alpha = 0.85f), modifier = Modifier.padding(horizontal = 6.dp),
                )
            }
            for (w in list) {
                val info = remember(w.id) { try { Widgets.awm(ctx).getAppWidgetInfo(w.id) } catch (_: Throwable) { null } }
                Box(
                    Modifier.fillMaxWidth().height(w.h.dp).clip(RoundedCornerShape(16.dp)),
                ) {
                    if (info == null) {
                        Box(
                            Modifier.fillMaxSize().background(pal.surf.copy(alpha = 0.8f)).border(1.dp, pal.line, RoundedCornerShape(16.dp)),
                            contentAlignment = Alignment.Center,
                        ) { Txt("Widget unavailable (app removed?)", size = 12f, color = pal.sub) }
                    } else {
                        AndroidView(
                            factory = { c ->
                                try {
                                    Widgets.host(c).createView(c, w.id, info).also { it.setPadding(0, 0, 0, 0) }
                                } catch (_: Throwable) { android.view.View(c) }
                            },
                            update = { v ->
                                try { (v as? android.appwidget.AppWidgetHostView)?.updateAppWidgetSize(Bundle(), wDp, w.h, wDp, w.h) } catch (_: Throwable) { }
                            },
                            modifier = Modifier.fillMaxSize(),
                        )
                    }
                    // Small edit handle: resize, move, remove.
                    Box(
                        Modifier.align(Alignment.TopEnd).padding(6.dp).size(32.dp).clip(RoundedCornerShape(50))
                            .background(pal.bg.copy(alpha = 0.62f)).border(1.dp, pal.line, RoundedCornerShape(50))
                            .clickable { onMenu(w) },
                        contentAlignment = Alignment.Center,
                    ) { GlyphIcon("gear", pal.acc, 16.dp) }
                }
            }
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(pal.bg.copy(alpha = 0.6f))
                    .border(1.dp, pal.acc.copy(alpha = 0.7f), RoundedCornerShape(12.dp)).clickable { onAdd() }.padding(14.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center,
            ) {
                GlyphIcon("plus", pal.acc, 20.dp)
                Txt("Add widget", size = 13.5f, bold = true, color = pal.acc, modifier = Modifier.padding(start = 10.dp))
            }
        }
    }
}

private class ProviderRow(val info: AppWidgetProviderInfo, val label: String, val app: String, val preview: Bitmap?)

/** Full-screen list of the widgets installed apps offer. */
@Composable
fun WidgetPicker(pal: Palette, onPick: (AppWidgetProviderInfo) -> Unit, onClose: () -> Unit) {
    val ctx = LocalContext.current
    val rows by produceState<List<ProviderRow>?>(null) {
        value = withContext(Dispatchers.Default) {
            val pm = ctx.packageManager
            val dpi = ctx.resources.displayMetrics.densityDpi
            val l = try { Widgets.awm(ctx).installedProviders } catch (_: Throwable) { emptyList() }
            l.map { i ->
                val label = try { i.loadLabel(pm) } catch (_: Throwable) { i.provider.shortClassName }
                val app = try { pm.getApplicationLabel(pm.getApplicationInfo(i.provider.packageName, 0)).toString() } catch (_: Throwable) { i.provider.packageName }
                val prev = try { i.loadPreviewImage(ctx, dpi)?.let { drawableToBitmap(it, 360) } } catch (_: Throwable) { null }
                ProviderRow(i, label.toString(), app, prev)
            }.sortedWith(compareBy({ it.app.lowercase() }, { it.label.lowercase() }))
        }
    }
    Column(Modifier.fillMaxSize().background(pal.bg).statusBarsPadding().clickable(remember { androidx.compose.foundation.interaction.MutableInteractionSource() }, null) { }) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(44.dp).clip(RoundedCornerShape(6.dp)).clickable { onClose() }, contentAlignment = Alignment.Center) {
                GlyphIcon("back", pal.acc, 24.dp, 2.2f)
            }
            Column(Modifier.padding(start = 8.dp)) {
                Txt("ADD WIDGET", size = 17f, bold = true, spacing = 1.2f)
                Txt("Widgets offered by your installed apps", size = 12f, color = pal.sub, maxLines = 1)
            }
        }
        Box(Modifier.fillMaxWidth().height(1.dp).background(pal.line))
        val r = rows
        Box(Modifier.weight(1f).navigationBarsPadding()) {
            if (r == null) Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Txt("Loading…", color = pal.sub) }
            else if (r.isEmpty()) Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
                Txt("No app on this phone offers widgets.", color = pal.sub)
            }
            else LazyColumn(Modifier.fillMaxSize().padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                item { Box(Modifier.height(6.dp)) }
                items(r, key = { it.info.provider.flattenToString() }) { row ->
                    val d = ctx.resources.displayMetrics.density
                    Row(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).background(pal.surf)
                            .border(1.dp, pal.line, RoundedCornerShape(8.dp)).clickable { onPick(row.info) }.padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(
                            Modifier.width(96.dp).height(64.dp).clip(RoundedCornerShape(6.dp)).background(pal.surf2),
                            contentAlignment = Alignment.Center,
                        ) {
                            if (row.preview != null) Image(row.preview.asImageBitmap(), null, Modifier.fillMaxSize().padding(4.dp), contentScale = ContentScale.Fit)
                            else GlyphIcon("grid", pal.acc, 26.dp)
                        }
                        Column(Modifier.padding(start = 12.dp).weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Txt(row.label, size = 14f, bold = true, maxLines = 1)
                            Txt(row.app, size = 12f, color = pal.sub, maxLines = 1)
                            Txt("~${(row.info.minWidth / d).toInt()} × ${(row.info.minHeight / d).toInt()} dp", size = 11f, color = pal.sub)
                        }
                    }
                }
                item { Box(Modifier.height(16.dp)) }
            }
        }
    }
    BackHandler { onClose() }
}

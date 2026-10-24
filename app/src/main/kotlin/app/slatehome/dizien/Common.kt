package app.slatehome.dizien

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class SheetAction(val glyph: String, val label: String, val danger: Boolean = false, val run: () -> Unit)

/** Bottom action sheet used for long-press menus and small notices. */
@Composable
fun ActionSheet(title: String, subtitle: String? = null, actions: List<SheetAction>, onDismiss: () -> Unit) {
    val p = LocalPal.current
    Box(
        Modifier.fillMaxSize().background(Color(0x99000000))
            .clickable(remember { MutableInteractionSource() }, null) { onDismiss() },
        contentAlignment = Alignment.BottomCenter,
    ) {
        Column(
            Modifier.fillMaxWidth().padding(12.dp).navigationBarsPadding()
                .clip(RoundedCornerShape(10.dp)).background(p.surf)
                .border(1.dp, p.line, RoundedCornerShape(10.dp))
                .clickable(remember { MutableInteractionSource() }, null) { }
                .padding(vertical = 6.dp),
        ) {
            Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                Txt(title, size = 15.5f, bold = true, maxLines = 2)
                if (subtitle != null) Txt(subtitle, size = 12.5f, color = p.sub, modifier = Modifier.padding(top = 4.dp))
            }
            RowDivider()
            actions.forEach { a ->
                Row(
                    Modifier.fillMaxWidth().clickable { onDismiss(); a.run() }.padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    GlyphIcon(a.glyph, if (a.danger) p.acc2 else p.acc, 20.dp)
                    Txt(a.label, size = 14.5f, modifier = Modifier.padding(start = 14.dp), color = if (a.danger) p.acc2 else p.text)
                }
            }
        }
    }
}

fun toast(ctx: Context, s: String) = Toast.makeText(ctx, s, Toast.LENGTH_SHORT).show()

/** Handles taps on the bottom navigation from any screen. */
fun navigate(act: Activity, dest: String, current: String, onNoTerminal: () -> Unit) {
    if (dest == current) return
    val store = Store.get(act)
    when (dest) {
        "home" -> {
            act.startActivity(Intent(act, HomeActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP))
            if (act !is HomeActivity) act.finish()
        }
        "terminal" -> {
            val i = TerminalLink.intent(act, store.terminalPkg)
            if (i != null) act.startActivity(i) else onNoTerminal()
        }
        "look" -> {
            act.startActivity(Intent(act, LookActivity::class.java))
            if (act !is HomeActivity) act.finish()
        }
        "settings" -> {
            act.startActivity(Intent(act, SettingsActivity::class.java))
            if (act !is HomeActivity) act.finish()
        }
    }
    act.overridePendingTransition(0, 0)
}

fun noTerminalSheet(onChoose: () -> Unit): Pair<String, List<SheetAction>> =
    "No terminal app linked yet" to listOf(
        SheetAction("grid", "Choose an installed app as Terminal", run = onChoose),
    )

const val NO_TERMINAL_TEXT =
    "The Debian-style terminal is a separate app that will be released on its own. When it is installed it appears here automatically, or you can link any terminal app you already use."

/** Full-bleed wallpaper bitmap, rendered off the main thread for the current screen size. */
@Composable
fun WallpaperBackground(id: Int, modifier: Modifier = Modifier, version: Long = 0L, publishGlass: Boolean = false) {
    val ctx = LocalContext.current
    BoxWithConstraints(modifier) {
        val d = LocalDensity.current
        val w = with(d) { maxWidth.toPx() }.toInt().coerceAtLeast(1)
        val h = with(d) { maxHeight.toPx() }.toInt().coerceAtLeast(1)
        val bmp by produceState<ImageBitmap?>(WallCache.get(id, w, h, version), id, w, h, version) {
            // Keys changed: always resolve again, otherwise the previous wallpaper's bitmap stays on screen.
            value = WallCache.get(id, w, h, version) ?: withContext(Dispatchers.Default) {
                WallCache.put(id, w, h, version, Wallpapers.forId(ctx, id, w, h).asImageBitmap())
            }
        }
        val b = bmp
        val glassState = LocalGlass.current
        var origin by remember { mutableStateOf(androidx.compose.ui.geometry.Offset.Zero) }
        if (publishGlass && glassState != null && LocalPal.current.glass && b != null) {
            LaunchedEffect(b, origin) {
                val blurred = try { withContext(Dispatchers.Default) { GlassBlur.make(b) } } catch (_: Throwable) { null }
                glassState.value = blurred?.let { GlassBackdrop(it, w, h, origin) }
            }
        }
        if (b != null) Image(b, null, Modifier.fillMaxSize().onGloballyPositioned { origin = it.positionInWindow() }, contentScale = ContentScale.Crop)
        else Box(Modifier.fillMaxSize().background(Color(0xFF12100D)))
    }
}

object WallCache {
    private val map = object : android.util.LruCache<String, ImageBitmap>(4) {}
    private fun k(id: Int, w: Int, h: Int, v: Long) = "$id:$w:$h:$v"
    fun get(id: Int, w: Int, h: Int, v: Long): ImageBitmap? = map.get(k(id, w, h, v))
    fun put(id: Int, w: Int, h: Int, v: Long, b: ImageBitmap): ImageBitmap { map.put(k(id, w, h, v), b); return b }
}

@Composable
fun ItemIcon(item: LItem, look: LookState, pal: Palette, sizeDp: Float) {
    val img = rememberItemIcon(item, look, pal, sizeDp)
    if (img != null) Image(img, item.label, Modifier.size(sizeDp.dp))
    else Box(Modifier.size(sizeDp.dp))
}

@Composable
fun Spacer(h: Int = 0, w: Int = 0) {
    Box(Modifier.height(h.dp).width(w.dp))
}

@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun ChipRow(content: @Composable () -> Unit) {
    androidx.compose.foundation.layout.FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) { content() }
}

/** Dark icons in the status/navigation bar when the screen behind them is light. */
@Composable
fun SystemBarIcons(lightStatus: Boolean, lightNav: Boolean = lightStatus) {
    val view = LocalView.current
    SideEffect {
        var c: Context? = view.context
        while (c is android.content.ContextWrapper && c !is Activity) c = c.baseContext
        val w = (c as? Activity)?.window ?: return@SideEffect
        val ctl = WindowCompat.getInsetsController(w, view)
        ctl.isAppearanceLightStatusBars = lightStatus
        ctl.isAppearanceLightNavigationBars = lightNav
    }
}

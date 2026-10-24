package app.slatehome.dizien

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt
import java.util.Random

/**
 * "Liquid Glass" support.
 *
 * Real glass needs something to look through, so the Home wallpaper is blurred once (a small copy, box-blurred on the
 * CPU) and every glass panel draws the matching piece of that blurred copy behind a light tint, a soft highlight and a
 * light rim. This works the same on every Android version (no RenderEffect / blur-behind, so nothing can crash on
 * old phones) and costs almost nothing while scrolling because the blurred copy never changes.
 */
class GlassBackdrop(val img: ImageBitmap, val w: Int, val h: Int, val origin: Offset)

val LocalGlass = compositionLocalOf<MutableState<GlassBackdrop?>?> { null }

object GlassBlur {
    /** Blurred, small copy of [src]; it is stretched back to full size when drawn. */
    fun make(src: ImageBitmap): ImageBitmap {
        val sb = src.asAndroidBitmap()
        val sw = (sb.width / 14).coerceAtLeast(8); val sh = (sb.height / 14).coerceAtLeast(8)
        val small = Bitmap.createScaledBitmap(sb, sw, sh, true).copy(Bitmap.Config.ARGB_8888, true)
        val px = IntArray(sw * sh); small.getPixels(px, 0, sw, 0, 0, sw, sh)
        repeat(3) { boxBlur(px, sw, sh, 3) }
        small.setPixels(px, 0, sw, 0, 0, sw, sh)
        return small.asImageBitmap()
    }

    private fun boxBlur(px: IntArray, w: Int, h: Int, r: Int) {
        val tmp = IntArray(px.size)
        pass(px, tmp, w, h, r, true)
        pass(tmp, px, w, h, r, false)
    }

    private fun pass(src: IntArray, dst: IntArray, w: Int, h: Int, r: Int, horizontal: Boolean) {
        val lines = if (horizontal) h else w
        val len = if (horizontal) w else h
        for (l in 0 until lines) {
            var sr = 0; var sg = 0; var sb = 0; var n = 0
            fun at(i: Int) = if (horizontal) src[l * w + i] else src[i * w + l]
            for (i in 0..minOf(r, len - 1)) { val v = at(i); sr += v shr 16 and 255; sg += v shr 8 and 255; sb += v and 255; n++ }
            for (i in 0 until len) {
                val o = (255 shl 24) or ((sr / n) shl 16) or ((sg / n) shl 8) or (sb / n)
                if (horizontal) dst[l * w + i] = o else dst[i * w + l] = o
                val add = i + r + 1
                if (add < len) { val v = at(add); sr += v shr 16 and 255; sg += v shr 8 and 255; sb += v and 255; n++ }
                val rem = i - r
                if (rem >= 0) { val v = at(rem); sr -= v shr 16 and 255; sg -= v shr 8 and 255; sb -= v and 255; n-- }
            }
        }
    }
}

/**
 * Background for panels: a frosted-glass surface on the Liquid Glass theme (when the blurred wallpaper is available),
 * otherwise the plain [solid] colour used by every other theme.
 */
fun Modifier.glass(shape: Shape, pal: Palette, solid: Color, strength: Float = 1f): Modifier = composed {
    val st = LocalGlass.current
    if (!pal.glass || st == null) {
        this.clip(shape).background(solid)
    } else {
        var pos by remember { mutableStateOf(Offset.Zero) }
        this.onGloballyPositioned { pos = it.positionInWindow() }
            .clip(shape)
            .drawBehind {
                val g = st.value
                if (g != null) {
                    val o = pos - g.origin
                    drawImage(
                        g.img,
                        srcOffset = IntOffset.Zero, srcSize = IntSize(g.img.width, g.img.height),
                        dstOffset = IntOffset(-o.x.roundToInt(), -o.y.roundToInt()), dstSize = IntSize(g.w, g.h),
                        filterQuality = FilterQuality.Medium,
                    )
                } else {
                    drawRect(Color(0xFF1A2033))
                }
                // Dark-blue tint keeps white text readable on any wallpaper, then a soft light sheen on top.
                drawRect(Color(0xFF0B1226).copy(alpha = 0.34f * strength))
                drawRect(Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.22f * strength), Color.White.copy(alpha = 0.04f))))
            }
            .border(1.dp, Brush.linearGradient(listOf(Color.White.copy(alpha = 0.65f), Color.White.copy(alpha = 0.12f), Color.White.copy(alpha = 0.35f))), shape)
    }
}

/** Original, code-generated glass-style wallpapers (soft colour fields, glowing blobs and glass orbs). */
object GlassWalls {
    val names = listOf("Glass Aurora", "Glass Sunset", "Glass Ocean", "Glass Mint", "Midnight Orbs", "Glass Candy")
    const val FIRST = 18
    val LAST get() = FIRST + names.size - 1

    private class Blob(val fx: Float, val fy: Float, val fr: Float, val color: Int, val alpha: Float)
    private class Orb(val fx: Float, val fy: Float, val fr: Float, val color: Int)
    private class Spec(val sky: IntArray, val blobs: List<Blob>, val orbs: List<Orb>)

    private fun spec(i: Int): Spec = when (i) {
        0 -> Spec(
            intArrayOf(0xFF1B1464.toInt(), 0xFF7B2FF7.toInt(), 0xFFF107A3.toInt()),
            listOf(Blob(0.18f, 0.22f, 0.55f, 0xFF00E5FF.toInt(), 0.75f), Blob(0.9f, 0.5f, 0.6f, 0xFFFF5CC8.toInt(), 0.7f), Blob(0.3f, 0.92f, 0.6f, 0xFFFFA24C.toInt(), 0.65f)),
            listOf(Orb(0.72f, 0.2f, 0.17f, 0xFF7DE7FF.toInt()), Orb(0.22f, 0.58f, 0.11f, 0xFFFF9BE6.toInt()), Orb(0.78f, 0.78f, 0.2f, 0xFFB69BFF.toInt())),
        )
        1 -> Spec(
            intArrayOf(0xFF2B1055.toInt(), 0xFFD53369.toInt(), 0xFFFFB347.toInt()),
            listOf(Blob(0.85f, 0.15f, 0.55f, 0xFFFFD36B.toInt(), 0.7f), Blob(0.1f, 0.5f, 0.55f, 0xFF7B5CFF.toInt(), 0.6f), Blob(0.7f, 0.85f, 0.5f, 0xFFFF6B6B.toInt(), 0.6f)),
            listOf(Orb(0.25f, 0.2f, 0.15f, 0xFFFFC27A.toInt()), Orb(0.8f, 0.55f, 0.12f, 0xFFFF8FB0.toInt()), Orb(0.35f, 0.8f, 0.19f, 0xFFFFE08A.toInt())),
        )
        2 -> Spec(
            intArrayOf(0xFF021B3A.toInt(), 0xFF0B6FB8.toInt(), 0xFF2EE6D6.toInt()),
            listOf(Blob(0.2f, 0.18f, 0.6f, 0xFF2EE6D6.toInt(), 0.6f), Blob(0.9f, 0.6f, 0.55f, 0xFF5B8CFF.toInt(), 0.7f), Blob(0.2f, 0.9f, 0.5f, 0xFF9B7BFF.toInt(), 0.5f)),
            listOf(Orb(0.75f, 0.18f, 0.14f, 0xFF9BFFF1.toInt()), Orb(0.18f, 0.55f, 0.18f, 0xFF7AB8FF.toInt()), Orb(0.72f, 0.8f, 0.12f, 0xFFC4B0FF.toInt())),
        )
        3 -> Spec(
            intArrayOf(0xFF0F3D3A.toInt(), 0xFF1FA67A.toInt(), 0xFFD7F7A0.toInt()),
            listOf(Blob(0.8f, 0.2f, 0.6f, 0xFFB6FF8A.toInt(), 0.6f), Blob(0.1f, 0.55f, 0.5f, 0xFF2EE6C6.toInt(), 0.65f), Blob(0.6f, 0.95f, 0.5f, 0xFFFFE98A.toInt(), 0.5f)),
            listOf(Orb(0.25f, 0.2f, 0.17f, 0xFFA8FFE0.toInt()), Orb(0.82f, 0.5f, 0.13f, 0xFFE4FFB0.toInt()), Orb(0.3f, 0.8f, 0.15f, 0xFF7DFFD0.toInt())),
        )
        4 -> Spec(
            intArrayOf(0xFF05060F.toInt(), 0xFF0C1230.toInt(), 0xFF1A1450.toInt()),
            listOf(Blob(0.2f, 0.25f, 0.5f, 0xFF5B3CFF.toInt(), 0.5f), Blob(0.85f, 0.7f, 0.5f, 0xFF00B3FF.toInt(), 0.45f)),
            listOf(Orb(0.75f, 0.17f, 0.15f, 0xFF8F7BFF.toInt()), Orb(0.2f, 0.45f, 0.1f, 0xFF4DD2FF.toInt()), Orb(0.7f, 0.5f, 0.07f, 0xFFFF7AD9.toInt()),
                Orb(0.3f, 0.75f, 0.17f, 0xFF6C8CFF.toInt()), Orb(0.8f, 0.85f, 0.1f, 0xFF9B6BFF.toInt())),
        )
        else -> Spec(
            intArrayOf(0xFF5B247A.toInt(), 0xFFE4508F.toInt(), 0xFFFFC3A0.toInt()),
            listOf(Blob(0.15f, 0.2f, 0.55f, 0xFF9B7BFF.toInt(), 0.6f), Blob(0.9f, 0.45f, 0.55f, 0xFFFFD0E8.toInt(), 0.6f), Blob(0.4f, 0.95f, 0.55f, 0xFFFF9A7B.toInt(), 0.6f)),
            listOf(Orb(0.75f, 0.2f, 0.16f, 0xFFFFC0E4.toInt()), Orb(0.2f, 0.6f, 0.14f, 0xFFD2B6FF.toInt()), Orb(0.7f, 0.8f, 0.19f, 0xFFFFD6A8.toInt())),
        )
    }

    fun draw(c: Canvas, w: Float, h: Float, id: Int) {
        val i = (id - FIRST).coerceIn(0, names.size - 1)
        val sp = spec(i)
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        p.shader = LinearGradient(0f, 0f, w * 0.7f, h, sp.sky, floatArrayOf(0f, 0.5f, 1f), Shader.TileMode.CLAMP)
        c.drawRect(0f, 0f, w, h, p)
        val big = maxOf(w, h)
        for (b in sp.blobs) {
            val r = b.fr * big
            val a = (b.alpha * 255).toInt()
            val col = (a shl 24) or (b.color and 0xFFFFFF)
            p.shader = RadialGradient(b.fx * w, b.fy * h, r, intArrayOf(col, (col and 0xFFFFFF) or ((a / 3) shl 24), b.color and 0xFFFFFF), floatArrayOf(0f, 0.55f, 1f), Shader.TileMode.CLAMP)
            c.drawCircle(b.fx * w, b.fy * h, r, p)
        }
        for (o in sp.orbs) orb(c, o.fx * w, o.fy * h, o.fr * w, o.color)
        // Fine light grain of tiny glints for a "liquid" sparkle, deterministic per wallpaper.
        val rnd = Random(97L + id)
        p.shader = null; p.style = Paint.Style.FILL
        repeat(60) {
            p.color = 0xFFFFFFFF.toInt(); p.alpha = (20 + rnd.nextInt(60))
            c.drawCircle(rnd.nextFloat() * w, rnd.nextFloat() * h, 0.8f + rnd.nextFloat() * 2.2f, p)
        }
    }

    private fun orb(c: Canvas, cx: Float, cy: Float, r: Float, color: Int) {
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        val rgb = color and 0xFFFFFF
        // soft glow
        p.shader = RadialGradient(cx, cy, r * 1.7f, intArrayOf((90 shl 24) or rgb, rgb), floatArrayOf(0.5f, 1f), Shader.TileMode.CLAMP)
        c.drawCircle(cx, cy, r * 1.7f, p)
        // glass sphere body
        p.shader = RadialGradient(
            cx - r * 0.3f, cy - r * 0.38f, r * 1.35f,
            intArrayOf(0xE6FFFFFF.toInt(), (170 shl 24) or rgb, (95 shl 24) or rgb, (150 shl 24) or rgb),
            floatArrayOf(0f, 0.35f, 0.8f, 1f), Shader.TileMode.CLAMP,
        )
        c.drawCircle(cx, cy, r, p)
        // light rim
        p.shader = LinearGradient(cx - r, cy - r, cx + r, cy + r, intArrayOf(0xDDFFFFFF.toInt(), 0x22FFFFFF, 0x99FFFFFF.toInt()), floatArrayOf(0f, 0.55f, 1f), Shader.TileMode.CLAMP)
        p.style = Paint.Style.STROKE; p.strokeWidth = maxOf(1.5f, r * 0.035f)
        c.drawCircle(cx, cy, r, p)
        // small highlight
        p.shader = null; p.style = Paint.Style.FILL; p.color = 0xFFFFFFFF.toInt(); p.alpha = 150
        c.drawOval(RectF(cx - r * 0.55f, cy - r * 0.78f, cx - r * 0.05f, cy - r * 0.5f), p)
    }
}

package app.slatehome.dizien

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.drawable.AdaptiveIconDrawable
import android.graphics.drawable.Drawable
import android.os.Build
import android.util.LruCache
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.core.graphics.PathParser
import androidx.core.graphics.createBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Draws the three icon styles from the mockups (Amber Line, Solid Tile, Round Glow).
 * No third-party icon pack is used: built-in shortcuts use the small original line glyphs in [Glyphs];
 * installed apps keep their own icon, either in original colours inside the style's tile shape or
 * re-tinted to the theme ("Theme tint").
 */
object IconRenderer {
    private fun argb(c: androidx.compose.ui.graphics.Color) = c.toArgb()
    private fun lerp(a: Int, b: Int, t: Float): Int {
        fun ch(s: Int) = ((a shr s and 255) + ((b shr s and 255) - (a shr s and 255)) * t).toInt().coerceIn(0, 255)
        return (255 shl 24) or (ch(16) shl 16) or (ch(8) shl 8) or ch(0)
    }

    private class Spec(val shape: Int, val fill: Int, val stroke: Boolean, val onAcc: Boolean, val shadow: Boolean = false)
    private const val SH_RR = 0; private const val SH_CIRCLE = 1; private const val SH_HEX = 2; private const val SH_LEAF = 3; private const val SH_NONE = 4
    private const val F_BG = 0; private const val F_ACC = 1; private const val F_GRAD = 2; private const val F_SURF = 3; private const val F_SURF2 = 4; private const val F_NONE = 5; private const val F_GLASS = 6
    private fun spec(style: Int) = when (style) {
        IconStyle.LINE -> Spec(SH_RR, F_BG, true, false)
        IconStyle.SOLID -> Spec(SH_RR, F_ACC, false, true)
        IconStyle.ROUND -> Spec(SH_CIRCLE, F_GRAD, false, true, true)
        IconStyle.SOFT -> Spec(SH_RR, F_SURF2, true, false)
        IconStyle.GRAD -> Spec(SH_RR, F_GRAD, false, true, true)
        IconStyle.RING -> Spec(SH_CIRCLE, F_BG, true, false)
        IconStyle.HEX -> Spec(SH_HEX, F_SURF, true, false)
        IconStyle.LEAF -> Spec(SH_LEAF, F_ACC, false, true)
        IconStyle.BARE -> Spec(SH_NONE, F_NONE, false, false)
        IconStyle.GLASS -> Spec(SH_RR, F_GLASS, false, false)
        else -> Spec(SH_RR, F_BG, true, false)
    }
    private fun shapePath(shape: Int, r: RectF): Path = Path().apply {
        val w = r.width()
        when (shape) {
            SH_CIRCLE -> addOval(r, Path.Direction.CW)
            SH_HEX -> {
                val rad = w / 2f
                for (k in 0 until 6) {
                    val ang = Math.toRadians(-90.0 + 60.0 * k)
                    val x = r.centerX() + (rad * Math.cos(ang)).toFloat(); val y = r.centerY() + (rad * Math.sin(ang)).toFloat()
                    if (k == 0) moveTo(x, y) else lineTo(x, y)
                }
                close()
            }
            SH_LEAF -> {
                val big = w * 0.40f; val small = w * 0.10f
                addRoundRect(r, floatArrayOf(big, big, small, small, big, big, small, small), Path.Direction.CW)
            }
            else -> addRoundRect(r, w * 0.19f, w * 0.19f, Path.Direction.CW)
        }
    }
    private fun drawTile(c: Canvas, style: Int, pal: Palette, r: RectF, size: Int, withFill: Boolean = true) {
        val sp = spec(style)
        if (sp.shape == SH_NONE) return
        if (style == IconStyle.GLASS) { drawGlassTile(c, pal, r, size, withFill); return }
        if (withFill && sp.fill != F_NONE) {
            val p = Paint(Paint.ANTI_ALIAS_FLAG)
            when (sp.fill) {
                F_BG -> { p.color = argb(pal.bg); p.alpha = 190 }
                F_ACC -> p.color = argb(pal.acc)
                F_GRAD -> p.shader = LinearGradient(r.left, r.top, r.right, r.bottom, argb(pal.acc), argb(pal.acc2), Shader.TileMode.CLAMP)
                F_SURF -> p.color = argb(pal.surf)
                else -> p.color = argb(pal.surf2)
            }
            if (sp.shadow) p.setShadowLayer(size * 0.05f, 0f, size * 0.03f, 0x66000000)
            c.drawPath(shapePath(sp.shape, r), p)
        }
        if (sp.stroke) {
            val sw = size * 0.03f
            val ri = RectF(r).apply { inset(sw / 2, sw / 2) }
            val s = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = if (style == IconStyle.SOFT) argb(pal.line) else argb(pal.acc); this.style = Paint.Style.STROKE; strokeWidth = sw; strokeJoin = Paint.Join.ROUND }
            c.drawPath(shapePath(sp.shape, ri), s)
        }
    }
    /** Glossy translucent tile with a light rim ("Liquid Glass" icon style). */
    private fun drawGlassTile(c: Canvas, pal: Palette, r: RectF, size: Int, withFill: Boolean) {
        val path = shapePath(SH_RR, r)
        if (withFill) {
            val p = Paint(Paint.ANTI_ALIAS_FLAG)
            p.color = argb(pal.acc); p.alpha = 34
            c.drawPath(path, p)
            p.color = 0xFFFFFFFF.toInt(); p.alpha = 255
            p.shader = LinearGradient(r.left, r.top, r.left, r.bottom, intArrayOf(0x66FFFFFF, 0x1AFFFFFF, 0x33FFFFFF), floatArrayOf(0f, 0.6f, 1f), Shader.TileMode.CLAMP)
            c.drawPath(path, p)
        }
        // gloss on the upper half
        c.save(); c.clipPath(path)
        val g = Paint(Paint.ANTI_ALIAS_FLAG)
        g.shader = LinearGradient(r.left, r.top, r.left, r.top + r.height() * 0.55f, 0x55FFFFFF, 0x00FFFFFF, Shader.TileMode.CLAMP)
        c.drawRect(r.left, r.top, r.right, r.top + r.height() * 0.55f, g)
        c.restore()
        val rim = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE; strokeWidth = size * 0.03f
            shader = LinearGradient(r.left, r.top, r.right, r.bottom, intArrayOf(0xEEFFFFFF.toInt(), 0x33FFFFFF, 0xAAFFFFFF.toInt()), floatArrayOf(0f, 0.55f, 1f), Shader.TileMode.CLAMP)
        }
        c.drawPath(shapePath(SH_RR, RectF(r).apply { inset(size * 0.015f, size * 0.015f) }), rim)
    }
    private fun tileRect(size: Int): RectF {
        val pad = size * 0.07f
        return RectF(pad, pad, size - pad, size - pad)
    }

    /** A built-in glyph tile. [name] is a key of [Glyphs.paths]. */
    fun glyph(name: String, style: Int, pal: Palette, size: Int): Bitmap {
        val bmp = createBitmap(size, size)
        val c = Canvas(bmp)
        val r = tileRect(size)
        drawTile(c, style, pal, r, size)
        val path = PathParser.createPathFromPathData(Glyphs.paths[name] ?: Glyphs.paths["box"]!!)
        val gs = r.width() * (if (style == IconStyle.BARE) 0.82f else 0.52f)
        val scale = gs / 24f
        c.save()
        c.translate(r.centerX() - gs / 2, r.centerY() - gs / 2)
        c.scale(scale, scale)
        val sp = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.style = Paint.Style.STROKE; strokeWidth = 1.8f; strokeCap = Paint.Cap.ROUND; strokeJoin = Paint.Join.ROUND
            color = if (style == IconStyle.GLASS) 0xFFFFFFFF.toInt() else if (spec(style).onAcc) argb(pal.accFg) else argb(pal.acc)
        }
        c.drawPath(path, sp)
        c.restore()
        return bmp
    }

    private fun meanLuma(b: Bitmap): Float {
        val s = Bitmap.createScaledBitmap(b, 24, 24, true)
        val px = IntArray(24 * 24); s.getPixels(px, 0, 24, 0, 0, 24, 24)
        var sum = 0f; var wsum = 0f
        for (v in px) {
            val a = (v ushr 24) / 255f
            if (a < 0.1f) continue
            val l = (0.299f * (v shr 16 and 255) + 0.587f * (v shr 8 and 255) + 0.114f * (v and 255)) / 255f
            sum += l * a; wsum += a
        }
        s.recycle()
        return if (wsum == 0f) 1f else sum / wsum
    }

    private fun coverage(b: Bitmap): Float {
        val s = Bitmap.createScaledBitmap(b, 24, 24, true)
        val px = IntArray(24 * 24); s.getPixels(px, 0, 24, 0, 0, 24, 24)
        val n = px.count { (it ushr 24) > 200 }
        s.recycle()
        return n / (24f * 24f)
    }

    private fun drawableToBitmap(d: Drawable, w: Int, h: Int): Bitmap {
        val b = createBitmap(w, h)
        val c = Canvas(b)
        d.setBounds(0, 0, w, h)
        d.draw(c)
        return b
    }


    /**
     * Liquid Glass for every installed app: a frosted tile first, then the app's own art sitting
     * inside it. The icon's solid background (often white) is only laid over the glass at low
     * opacity, so the tile stays see-through, while the foreground keeps the app's colours.
     */
    private fun glassApp(c: Canvas, icon: Drawable, adaptive: Boolean, pal: Palette, r: RectF, size: Int) {
        drawGlassTile(c, pal, r, size, true)
        val inner = RectF(r).apply { inset(r.width() * 0.09f, r.width() * 0.09f) }
        val clip = shapePath(SH_RR, inner)
        c.save()
        c.clipPath(clip)
        if (adaptive) {
            val ad = icon as AdaptiveIconDrawable
            val big = inner.width() * 1.5f
            val l = inner.centerX() - big / 2; val t = inner.centerY() - big / 2
            val bounds = android.graphics.Rect(l.toInt(), t.toInt(), (l + big).toInt(), (t + big).toInt())
            ad.background?.let {
                c.saveLayerAlpha(inner, 120)
                it.bounds = bounds; it.draw(c)
                c.restore()
            }
            ad.foreground?.let { it.bounds = bounds; it.draw(c) }
        } else {
            // Old-style icons: soften the opaque square a little and keep it inside the glass.
            c.saveLayerAlpha(inner, 235)
            icon.setBounds(inner.left.toInt(), inner.top.toInt(), inner.right.toInt(), inner.bottom.toInt())
            icon.draw(c)
            c.restore()
        }
        c.restore()
        drawGlassTile(c, pal, r, size, false)
    }
    /** An installed app. */
    fun app(ctx: Context, app: AppEntry, style: Int, themeTint: Boolean, pal: Palette, size: Int): Bitmap {
        val icon: Drawable = try { app.info.getIcon(0) } catch (_: Exception) { ctx.packageManager.defaultActivityIcon }
        val bmp = createBitmap(size, size)
        val c = Canvas(bmp)
        val r = tileRect(size)
        val adaptive = Build.VERSION.SDK_INT >= 26 && icon is AdaptiveIconDrawable

        val (glyphLayer, mono) = run {
            var layer: Bitmap? = null
            var isMono = false
            // Theme tint needs the icon's own monochrome layer (Android 13+ themed icons); other icons keep their colours.
            if (themeTint && adaptive && Build.VERSION.SDK_INT >= 33) {
                val ad = icon as AdaptiveIconDrawable
                val g = (r.width() * 1.05f).toInt()
                val monoLayer = ad.monochrome
                if (monoLayer != null) { layer = drawableToBitmap(monoLayer, g, g); isMono = true }
            }
            layer to isMono
        }

        if (glyphLayer != null) {
            drawTile(c, style, pal, r, size)
            val g = glyphLayer.width
            val tintPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
            val strong = if (style == IconStyle.GLASS) 0xFFFFFFFF.toInt() else if (!spec(style).onAcc) argb(pal.acc) else argb(pal.accFg)
            if (mono) {
                tintPaint.colorFilter = PorterDuffColorFilter(strong, PorterDuff.Mode.SRC_IN)
            } else {
                val weak = if (!spec(style).onAcc) lerp(argb(pal.bg), argb(pal.acc), 0.45f) else lerp(argb(pal.accFg), argb(pal.acc), 0.5f)
                val lightGlyph = meanLuma(glyphLayer) >= 0.5f
                val a = if (lightGlyph) 1f else -1f
                val b0 = if (lightGlyph) 0f else 1f
                fun row(sh: Int): FloatArray {
                    val w = (weak shr sh and 255).toFloat(); val s = (strong shr sh and 255).toFloat()
                    val d = (s - w) * a / 255f
                    return floatArrayOf(d * 0.299f, d * 0.587f, d * 0.114f, 0f, w + (s - w) * b0)
                }
                val m = ColorMatrix(floatArrayOf(*row(16), *row(8), *row(0), 0f, 0f, 0f, 1f, 0f))
                tintPaint.colorFilter = ColorMatrixColorFilter(m)
            }
            c.drawBitmap(glyphLayer, r.centerX() - g / 2f, r.centerY() - g / 2f, tintPaint)
            glyphLayer.recycle()
        } else if (style == IconStyle.GLASS) {
            glassApp(c, icon, adaptive, pal, r, size)
        } else {
            // Original colours, clipped to the style's tile shape.
            val sp = spec(style)
            if (sp.shadow) {
                val sh = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = argb(pal.surf2); setShadowLayer(size * 0.05f, 0f, size * 0.03f, 0x66000000) }
                c.drawPath(shapePath(sp.shape, r), sh)
            }
            if (style == IconStyle.GLASS) drawTile(c, style, pal, r, size)
            c.save()
            c.clipPath(shapePath(sp.shape, r))
            // Apps without a monochrome layer: restyle their picture as a two-tone image in the theme colours.
            var duoLayer = false
            if (themeTint && style != IconStyle.GLASS) {
                val strong2 = if (!sp.onAcc) argb(pal.acc) else lerp(argb(pal.acc), 0xFFFFFFFF.toInt(), 0.35f)
                val weak2 = if (!sp.onAcc) lerp(argb(pal.bg), argb(pal.acc), 0.14f) else argb(pal.accFg)
                fun duo(sh: Int): FloatArray {
                    val w = (weak2 shr sh and 255).toFloat(); val s2 = (strong2 shr sh and 255).toFloat()
                    val d = (s2 - w) / 255f
                    return floatArrayOf(d * 0.299f, d * 0.587f, d * 0.114f, 0f, w)
                }
                val dm = ColorMatrix(floatArrayOf(*duo(16), *duo(8), *duo(0), 0f, 0f, 0f, 1f, 0f))
                val lp = Paint().apply { colorFilter = ColorMatrixColorFilter(dm) }
                c.saveLayer(r, lp); duoLayer = true
            }
            if (adaptive) {
                val ad = icon as AdaptiveIconDrawable
                val big = r.width() * 1.5f
                val l = r.centerX() - big / 2; val t = r.centerY() - big / 2
                ad.background?.let { it.setBounds(l.toInt(), t.toInt(), (l + big).toInt(), (t + big).toInt()); it.draw(c) }
                ad.foreground?.let { it.setBounds(l.toInt(), t.toInt(), (l + big).toInt(), (t + big).toInt()); it.draw(c) }
            } else {
                icon.setBounds(r.left.toInt(), r.top.toInt(), r.right.toInt(), r.bottom.toInt())
                icon.draw(c)
            }
            if (duoLayer) c.restore()
            c.restore()
            if (spec(style).stroke || style == IconStyle.GLASS) drawTile(c, style, pal, r, size, withFill = false)
        }

        if (app.user != android.os.Process.myUserHandle()) {
            val bp = Paint(Paint.ANTI_ALIAS_FLAG)
            val rr = size * 0.13f
            bp.color = argb(pal.bg); c.drawCircle(size - rr - 2, size - rr - 2, rr, bp)
            bp.color = argb(pal.acc); c.drawCircle(size - rr - 2, size - rr - 2, rr * 0.62f, bp)
        }
        return bmp
    }

    val cache = object : LruCache<String, ImageBitmap>(240) {}
}

/** Loads (and caches) the styled icon for [item] off the main thread. */
@Composable
fun rememberItemIcon(item: LItem, look: LookState, pal: Palette, sizeDp: Float): ImageBitmap? {
    val ctx = LocalContext.current
    val px = (sizeDp * LocalDensity.current.density).toInt().coerceAtLeast(16)
    val key = "${item.key}|${look.iconStyle}|${look.themeTint}|${pal.id}|$px"
    val state by produceState<ImageBitmap?>(IconRenderer.cache.get(key), key) {
        IconRenderer.cache.get(key)?.let { value = it; return@produceState }
        value = withContext(Dispatchers.Default) {
            val bmp = if (item.app != null) IconRenderer.app(ctx, item.app, look.iconStyle, look.themeTint, pal, px)
            else IconRenderer.glyph(item.glyph ?: "box", look.iconStyle, pal, px)
            bmp.asImageBitmap().also { IconRenderer.cache.put(key, it) }
        }
    }
    return state
}

/** Sample glyph tile for the Look screen's icon-style previews. */
@Composable
fun rememberGlyphTile(glyph: String, style: Int, pal: Palette, sizeDp: Float): ImageBitmap {
    val px = (sizeDp * LocalDensity.current.density).toInt()
    return remember(glyph, style, pal.id, px) { IconRenderer.glyph(glyph, style, pal, px).asImageBitmap() }
}

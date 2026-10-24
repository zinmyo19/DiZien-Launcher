package app.slatehome.dizien

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.DashPathEffect
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.Shader
import java.io.File
import java.util.Random

/**
 * All wallpapers are drawn by code at runtime. They are original artwork made for this project
 * (concentric broken rings, grid, waves, stars, gradients) and are released under GPL-3.0 with the rest
 * of the source; no third-party image files are bundled.
 */
object Wallpapers {
    val names = listOf("Amber Dusk", "Swirl Lines", "Graphite Grid", "Ember Wave", "Starfield", "Copper Fade") + WallpapersExtra.names + GlassWalls.names

    private fun rings(c: Canvas, w: Float, h: Float, color: Int, alpha: Float, n: Int) {
        val cx = w * 0.62f; val cy = h * 0.42f
        val p = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; this.color = color; strokeWidth = maxOf(2f, w * 0.0045f) }
        val u = w / 190f
        for (k in 0 until n) {
            p.alpha = (255 * alpha * (1f - k / (n + 2f))).toInt().coerceIn(0, 255)
            p.pathEffect = DashPathEffect(floatArrayOf((200 - k * 18) * u, (40 + k * 9) * u), 0f)
            c.save(); c.rotate(k * 38f, cx, cy)
            c.drawCircle(cx, cy, (14 + k * 21) * u, p)
            c.restore()
        }
    }

    fun draw(c: Canvas, w: Float, h: Float, id: Int) {
        val fill = Paint(Paint.ANTI_ALIAS_FLAG)
        when (id) {
            0 -> {
                fill.shader = LinearGradient(0f, 0f, w * 0.4f, h, intArrayOf(0xFF1B1511.toInt(), 0xFF5E2F12.toInt(), 0xFFFF9A1F.toInt()), floatArrayOf(0f, 0.6f, 1f), Shader.TileMode.CLAMP)
                c.drawRect(0f, 0f, w, h, fill)
                rings(c, w, h, 0xFFFFC878.toInt(), 0.42f, 7)
            }
            1 -> {
                fill.color = 0xFF17130F.toInt(); c.drawRect(0f, 0f, w, h, fill)
                rings(c, w, h, 0xFFFFB020.toInt(), 0.8f, 9)
            }
            2 -> {
                fill.color = 0xFF1F1A15.toInt(); c.drawRect(0f, 0f, w, h, fill)
                val lp = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFFFB020.toInt(); alpha = 30; strokeWidth = 1.5f; style = Paint.Style.STROKE }
                val step = w * 22f / 190f
                var x = 0f; while (x <= w) { c.drawLine(x, 0f, x, h, lp); x += step }
                var y = 0f; while (y <= h) { c.drawLine(0f, y, w, y, lp); y += step }
                val cp = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFFFB020.toInt(); style = Paint.Style.STROKE; strokeWidth = maxOf(3f, w * 0.01f) }
                c.drawCircle(w * 0.5f, h * 0.5f, w * 0.28f, cp)
            }
            3 -> {
                fill.color = 0xFF1A0B10.toInt(); c.drawRect(0f, 0f, w, h, fill)
                for (k in 0 until 5) {
                    val path = Path()
                    path.moveTo(0f, h * (0.45f + k * 0.09f))
                    path.cubicTo(w * 0.3f, h * (0.35f + k * 0.09f), w * 0.6f, h * (0.6f + k * 0.09f), w, h * (0.42f + k * 0.09f))
                    path.lineTo(w, h); path.lineTo(0f, h); path.close()
                    fill.color = 0xFFA80030.toInt(); fill.alpha = ((0.18f + k * 0.1f) * 255).toInt()
                    c.drawPath(path, fill)
                }
            }
            4 -> {
                fill.color = 0xFF0F0D0B.toInt(); c.drawRect(0f, 0f, w, h, fill)
                fill.color = 0xFFFFB020.toInt(); fill.alpha = 26
                c.drawCircle(w * 0.5f, h * 0.72f, w * 0.5f, fill)
                val rnd = Random(4)
                val radii = floatArrayOf(0.6f, 0.9f, 1.3f)
                val u = maxOf(1.6f, w / 190f)
                repeat(170) {
                    fill.color = 0xFFFFE2A8.toInt()
                    fill.alpha = ((0.3f + rnd.nextFloat() * 0.6f) * 255).toInt()
                    c.drawCircle(rnd.nextFloat() * w, rnd.nextFloat() * h, radii[rnd.nextInt(3)] * u, fill)
                }
            }
            in WallpapersExtra.FIRST..17 -> WallpapersExtra.draw(c, w, h, id)
            in GlassWalls.FIRST..GlassWalls.LAST -> GlassWalls.draw(c, w, h, id)
            else -> {
                fill.shader = RadialGradient(w * 0.3f, h * 0.25f, h * 0.95f, intArrayOf(0xFF8A4B1C.toInt(), 0xFF2D1D12.toInt(), 0xFF120E0B.toInt()), floatArrayOf(0f, 0.55f, 1f), Shader.TileMode.CLAMP)
                c.drawRect(0f, 0f, w, h, fill)
                rings(c, w, h, 0xFFD08A4A.toInt(), 0.35f, 6)
            }
        }
    }

    fun render(w: Int, h: Int, id: Int): Bitmap {
        val b = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        draw(Canvas(b), w.toFloat(), h.toFloat(), id)
        return b
    }

    fun customFile(ctx: Context) = File(ctx.filesDir, "wallpaper_custom.jpg")

    /** Decodes the user's own wallpaper image, cropped to fill w x h (centre crop), slightly dimmed for legibility. */
    fun renderCustom(ctx: Context, w: Int, h: Int): Bitmap? {
        val f = customFile(ctx)
        if (!f.exists()) return null
        val src = BitmapFactory.decodeFile(f.path) ?: return null
        val out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val c = Canvas(out)
        val scale = maxOf(w / src.width.toFloat(), h / src.height.toFloat())
        val dw = src.width * scale; val dh = src.height * scale
        val dst = android.graphics.RectF((w - dw) / 2f, (h - dh) / 2f, (w + dw) / 2f, (h + dh) / 2f)
        c.drawBitmap(src, null, dst, Paint(Paint.FILTER_BITMAP_FLAG))
        c.drawColor(0x55000000)
        src.recycle()
        return out
    }

    /** Copies a picked image into app-private storage, downscaled so it is cheap to draw. */
    fun importCustom(ctx: Context, uri: android.net.Uri): Boolean {
        return try {
            val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            ctx.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, opts) }
            var sample = 1
            while (maxOf(opts.outWidth, opts.outHeight) / sample > 2400) sample *= 2
            val o2 = BitmapFactory.Options().apply { inSampleSize = sample }
            val bmp = ctx.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, o2) } ?: return false
            customFile(ctx).outputStream().use { bmp.compress(Bitmap.CompressFormat.JPEG, 90, it) }
            bmp.recycle()
            true
        } catch (_: Exception) { false }
    }

    fun forId(ctx: Context, id: Int, w: Int, h: Int): Bitmap =
        (if (id == WALL_CUSTOM) renderCustom(ctx, w, h) else null) ?: render(w, h, if (id in names.indices) id else 0)
}

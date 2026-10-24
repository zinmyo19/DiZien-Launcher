package app.slatehome.dizien

import android.graphics.Canvas
import android.graphics.DashPathEffect
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import java.util.Random
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Wallpapers 6-17. Every one is original artwork drawn by code (gradients, waves, polygons, dots, lines)
 * for this project and released under GPL-3.0 with the source. No image files, no third-party art.
 */
object WallpapersExtra {
    val names = listOf(
        "Aurora Bands", "Dune Lines", "Midnight Peaks", "Low-Poly Cherry", "Blueprint", "Topo Lines",
        "Hex Mesh", "Dusk Gradient", "Halftone Amber", "Violet Orbit", "Forest Mist", "Fog Gray",
    )
    const val FIRST = 6

    private fun paint(color: Int, alpha: Int = 255) = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = color; this.alpha = alpha }
    private fun stroke(color: Int, alpha: Int, w: Float) = paint(color, alpha).apply { style = Paint.Style.STROKE; strokeWidth = w }
    private fun lerp(a: Int, b: Int, t: Float): Int {
        val k = t.coerceIn(0f, 1f)
        fun ch(s: Int) = ((a shr s and 255) + ((b shr s and 255) - (a shr s and 255)) * k).toInt().coerceIn(0, 255)
        return (255 shl 24) or (ch(16) shl 16) or (ch(8) shl 8) or ch(0)
    }
    private fun vgrad(c: Canvas, w: Float, h: Float, y0: Float, y1: Float, cols: IntArray, pos: FloatArray? = null) {
        val p = Paint().apply { shader = LinearGradient(0f, y0, 0f, y1, cols, pos, Shader.TileMode.CLAMP) }
        c.drawRect(0f, 0f, w, h, p)
    }
    private fun glow(c: Canvas, cx: Float, cy: Float, r: Float, color: Int, a: Int) {
        val p = Paint().apply {
            shader = RadialGradient(cx, cy, r, intArrayOf((a shl 24) or (color and 0xFFFFFF), color and 0xFFFFFF), null, Shader.TileMode.CLAMP)
        }
        c.drawCircle(cx, cy, r, p)
    }
    private fun stars(c: Canvas, w: Float, h: Float, seed: Long, n: Int, maxY: Float, color: Int = 0xFFFFFFFF.toInt()) {
        val rnd = Random(seed); val u = maxOf(1.5f, w / 190f); val p = paint(color)
        repeat(n) {
            p.alpha = (60 + rnd.nextInt(180))
            c.drawCircle(rnd.nextFloat() * w, rnd.nextFloat() * maxY, (0.5f + rnd.nextFloat() * 0.9f) * u, p)
        }
    }
    private fun waveLayer(c: Canvas, w: Float, h: Float, y0: Float, amp: Float, freq: Float, phase: Float, fill: Paint) {
        val path = Path(); val steps = 48
        for (i in 0..steps) {
            val x = w * i / steps
            val y = y0 + sin(x / w * 2f * PI.toFloat() * freq + phase) * amp + sin(x / w * 2f * PI.toFloat() * freq * 2.3f + phase * 1.7f) * amp * 0.35f
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        path.lineTo(w, h); path.lineTo(0f, h); path.close()
        c.drawPath(path, fill)
    }

    fun draw(c: Canvas, w: Float, h: Float, id: Int) {
        val u = w / 190f
        when (id) {
            6 -> { // Aurora Bands
                vgrad(c, w, h, 0f, h, intArrayOf(0xFF060A14.toInt(), 0xFF0E1A2B.toInt()))
                stars(c, w, h, 61, 70, h * 0.5f)
                glow(c, w * 0.15f, h * 0.22f, w * 0.7f, 0xFF3B82F6.toInt(), 120)
                glow(c, w * 0.55f, h * 0.30f, w * 0.65f, 0xFF1FD1B4.toInt(), 150)
                glow(c, w * 0.85f, h * 0.45f, w * 0.6f, 0xFF4ADE80.toInt(), 110)
                glow(c, w * 0.30f, h * 0.58f, w * 0.7f, 0xFF8B5CF6.toInt(), 120)
                vgrad(c, w, h, h * 0.65f, h, intArrayOf(0x00000000, 0xCC050810.toInt()))
            }
            7 -> { // Dune Lines
                vgrad(c, w, h, 0f, h * 0.62f, intArrayOf(0xFF241529.toInt(), 0xFFB5513A.toInt(), 0xFFF2A65A.toInt()), floatArrayOf(0f, 0.6f, 1f))
                c.drawCircle(w * 0.7f, h * 0.40f, w * 0.12f, paint(0xFFFFD08A.toInt(), 225))
                for (k in 0 until 6) {
                    val col = lerp(0xFF9A5128.toInt(), 0xFF1B0F08.toInt(), k / 5f)
                    waveLayer(c, w, h, h * (0.52f + k * 0.09f), h * (0.020f + k * 0.004f), 1f + k * 0.3f, k * 1.7f, paint(col))
                }
            }
            8 -> { // Midnight Peaks
                vgrad(c, w, h, 0f, h, intArrayOf(0xFF090E20.toInt(), 0xFF1C2A4A.toInt()))
                stars(c, w, h, 8, 90, h * 0.5f)
                glow(c, w * 0.72f, h * 0.20f, w * 0.22f, 0xFFBFD0FF.toInt(), 90)
                c.drawCircle(w * 0.72f, h * 0.20f, w * 0.065f, paint(0xFFE9EEFF.toInt(), 240))
                val rnd = Random(88)
                for (k in 0 until 4) {
                    val base = h * (0.56f + k * 0.10f); val amp = h * (0.13f - k * 0.015f)
                    val path = Path(); path.moveTo(0f, h)
                    val n = 11
                    for (i in 0..n) path.lineTo(w * i / n, base - rnd.nextFloat() * amp - (if (i % 2 == 0) amp * 0.4f else 0f))
                    path.lineTo(w, h); path.close()
                    c.drawPath(path, paint(lerp(0xFF2E3F66.toInt(), 0xFF05080F.toInt(), k / 3f)))
                }
            }
            9 -> { // Low-Poly Cherry
                c.drawColor(0xFF18181D.toInt())
                val cols = 7; val cell = w / cols; val rows = (h / cell).toInt() + 2
                val rnd = Random(9)
                val px = Array(rows + 1) { FloatArray(cols + 1) }; val py = Array(rows + 1) { FloatArray(cols + 1) }
                for (r in 0..rows) for (q in 0..cols) {
                    val edgeX = q == 0 || q == cols; val edgeY = r == 0 || r == rows
                    px[r][q] = q * cell + if (edgeX) 0f else (rnd.nextFloat() - 0.5f) * cell * 0.55f
                    py[r][q] = r * cell + if (edgeY) 0f else (rnd.nextFloat() - 0.5f) * cell * 0.55f
                }
                val lo = 0xFF1D1E25.toInt(); val hi = 0xFFD70A53.toInt()
                fun tri(x1: Float, y1: Float, x2: Float, y2: Float, x3: Float, y3: Float) {
                    val cx = (x1 + x2 + x3) / 3; val cy = (y1 + y2 + y3) / 3
                    val t = (cx / w * 0.35f + cy / h * 0.75f - 0.25f) * 1.15f + (rnd.nextFloat() - 0.5f) * 0.22f
                    val path = Path().apply { moveTo(x1, y1); lineTo(x2, y2); lineTo(x3, y3); close() }
                    c.drawPath(path, paint(lerp(lo, hi, t)))
                }
                for (r in 0 until rows) for (q in 0 until cols) {
                    tri(px[r][q], py[r][q], px[r][q + 1], py[r][q + 1], px[r + 1][q], py[r + 1][q])
                    tri(px[r][q + 1], py[r][q + 1], px[r + 1][q + 1], py[r + 1][q + 1], px[r + 1][q], py[r + 1][q])
                }
            }
            10 -> { // Blueprint
                c.drawColor(0xFF0F3057.toInt())
                val step = 8f * u
                val minor = stroke(0xFF9CC9FF.toInt(), 38, maxOf(1f, u * 0.4f)); val major = stroke(0xFF9CC9FF.toInt(), 95, maxOf(1.5f, u * 0.7f))
                var i = 0; var x = 0f
                while (x <= w) { c.drawLine(x, 0f, x, h, if (i % 5 == 0) major else minor); x += step; i++ }
                i = 0; var y = 0f
                while (y <= h) { c.drawLine(0f, y, w, y, if (i % 5 == 0) major else minor); y += step; i++ }
                val ln = stroke(0xFFE6F2FF.toInt(), 210, maxOf(2f, u * 0.9f))
                c.drawCircle(w * 0.5f, h * 0.45f, w * 0.32f, ln)
                val dash = stroke(0xFFE6F2FF.toInt(), 170, maxOf(1.5f, u * 0.7f)).apply { pathEffect = DashPathEffect(floatArrayOf(6 * u, 4 * u), 0f) }
                c.drawCircle(w * 0.5f, h * 0.45f, w * 0.19f, dash)
                c.drawLine(w * 0.5f - w * 0.4f, h * 0.45f, w * 0.5f + w * 0.4f, h * 0.45f, dash)
                c.drawLine(w * 0.5f, h * 0.45f - w * 0.4f, w * 0.5f, h * 0.45f + w * 0.4f, dash)
                c.drawRect(RectF(w * 0.12f, h * 0.78f, w * 0.88f, h * 0.9f), ln)
            }
            11 -> { // Topo Lines
                c.drawColor(0xFF0B1410.toInt())
                fun field(cx: Float, cy: Float, n: Int, gap: Float, seed: Float) {
                    for (k in 1..n) {
                        val base = k * gap
                        val path = Path(); val seg = 96
                        for (s in 0..seg) {
                            val a = s / seg.toFloat() * 2f * PI.toFloat()
                            val rr = base * (1f + 0.17f * sin(3f * a + k * 0.28f + seed) + 0.09f * sin(5f * a - k * 0.45f + seed * 2f))
                            val x = cx + cos(a) * rr; val y = cy + sin(a) * rr * 1.15f
                            if (s == 0) path.moveTo(x, y) else path.lineTo(x, y)
                        }
                        path.close()
                        c.drawPath(path, stroke(0xFF4ADE80.toInt(), (40 + k * 11).coerceAtMost(210), maxOf(1.5f, u * 0.75f)))
                    }
                }
                field(w * 0.30f, h * 0.38f, 17, w * 0.043f, 0f)
                field(w * 0.85f, h * 0.80f, 9, w * 0.045f, 2f)
            }
            12 -> { // Hex Mesh
                c.drawColor(0xFF08141A.toInt())
                val rad = w / 9f; val hw = sqrt(3f) * rad; val vs = 1.5f * rad
                val rnd = Random(12)
                var row = 0; var y = -rad
                while (y < h + rad) {
                    var x = if (row % 2 == 0) 0f else hw / 2f
                    x -= hw
                    while (x < w + hw) {
                        val d = hypot(x - w * 0.5f, y - h * 0.35f) / (h * 0.8f)
                        val a = (150 * (1f - d)).toInt().coerceIn(14, 150)
                        val path = Path()
                        for (k in 0 until 6) {
                            val ang = Math.toRadians(-90.0 + 60.0 * k)
                            val px = x + (rad * 0.94f * cos(ang)).toFloat(); val py = y + (rad * 0.94f * sin(ang)).toFloat()
                            if (k == 0) path.moveTo(px, py) else path.lineTo(px, py)
                        }
                        path.close()
                        if (rnd.nextInt(7) == 0) c.drawPath(path, paint(0xFF2DD4BF.toInt(), (a / 4).coerceAtLeast(8)))
                        c.drawPath(path, stroke(0xFF2DD4BF.toInt(), a, maxOf(1.5f, u * 0.8f)))
                        x += hw
                    }
                    y += vs; row++
                }
            }
            13 -> { // Dusk Gradient
                val hor = h * 0.72f
                val sky = Paint().apply {
                    shader = LinearGradient(0f, 0f, 0f, hor, intArrayOf(0xFF1A1033.toInt(), 0xFF5B2A86.toInt(), 0xFFE5566D.toInt(), 0xFFFFB454.toInt()), floatArrayOf(0f, 0.38f, 0.72f, 1f), Shader.TileMode.CLAMP)
                }
                c.drawRect(0f, 0f, w, hor, sky)
                c.save(); c.clipRect(0f, 0f, w, hor)
                c.drawCircle(w * 0.5f, hor, w * 0.17f, paint(0xFFFFE3A6.toInt(), 245))
                c.restore()
                val sea = Paint().apply { shader = LinearGradient(0f, hor, 0f, h, intArrayOf(0xFF2A1226.toInt(), 0xFF0A050E.toInt()), null, Shader.TileMode.CLAMP) }
                c.drawRect(0f, hor, w, h, sea)
                var y = hor + 3 * u; var k = 0
                while (y < h && k < 14) {
                    val half = w * 0.17f * (1f - k / 15f)
                    c.drawRect(w * 0.5f - half, y, w * 0.5f + half, y + 1.8f * u, paint(0xFFFFB454.toInt(), (190 - k * 12).coerceAtLeast(20)))
                    y += (5 + k * 1.2f) * u; k++
                }
            }
            14 -> { // Halftone Amber
                c.drawColor(0xFF14100C.toInt())
                val step = 7f * u; val p = paint(0xFFFFB020.toInt())
                var y = step / 2
                while (y < h) {
                    var x = step / 2
                    while (x < w) {
                        val d = hypot(x - w * 0.8f, y - h * 0.85f) / (h * 0.95f)
                        val f = Math.pow((1f - d).coerceIn(0f, 1f).toDouble(), 1.2).toFloat()
                        if (f > 0.03f) c.drawCircle(x, y, step * 0.52f * f, p)
                        x += step
                    }
                    y += step
                }
            }
            15 -> { // Violet Orbit
                c.drawColor(0xFF0E0A1A.toInt())
                val cx = w * 0.5f; val cy = h * 0.42f
                glow(c, cx, cy, h * 0.6f, 0xFF8B5CF6.toInt(), 110)
                stars(c, w, h, 15, 60, h, 0xFFE4D4FF.toInt())
                for (k in 1..6) {
                    val a = (14 + k * 15) * u; val b = a * 0.42f
                    c.save(); c.rotate(-24f + k * 7f, cx, cy)
                    c.drawOval(RectF(cx - a, cy - b, cx + a, cy + b), stroke(0xFFB48CFF.toInt(), (190 - k * 22).coerceAtLeast(40), maxOf(1.5f, u * 0.7f)))
                    val ang = k * 1.35f
                    c.drawCircle(cx + cos(ang) * a, cy + sin(ang) * b, (1.6f + (k % 3)) * u, paint(0xFFE4D4FF.toInt()))
                    c.restore()
                }
                glow(c, cx, cy, 16 * u, 0xFFE4D4FF.toInt(), 255)
                c.drawCircle(cx, cy, 5 * u, paint(0xFFFFFFFF.toInt()))
            }
            16 -> { // Forest Mist
                vgrad(c, w, h, 0f, h, intArrayOf(0xFF0B1814.toInt(), 0xFF1F3B30.toInt()))
                glow(c, w * 0.75f, h * 0.18f, w * 0.5f, 0xFFB8F0D0.toInt(), 70)
                for (k in 0 until 5) {
                    val y0 = h * (0.48f + k * 0.10f)
                    val col = lerp(0xFF2E5D47.toInt(), 0xFF06100B.toInt(), k / 4f)
                    waveLayer(c, w, h, y0, h * 0.025f, 0.8f + k * 0.35f, k * 2.1f, paint(col))
                    val fog = Paint().apply { shader = LinearGradient(0f, y0 - h * 0.03f, 0f, y0 + h * 0.10f, 0x55CFE8D8, 0x00CFE8D8, Shader.TileMode.CLAMP) }
                    c.drawRect(0f, y0 - h * 0.03f, w, y0 + h * 0.10f, fog)
                }
            }
            else -> { // 17 Fog Gray
                val p = Paint().apply { shader = LinearGradient(0f, 0f, w, h, 0xFF7C8796.toInt(), 0xFF2F3743.toInt(), Shader.TileMode.CLAMP) }
                c.drawRect(0f, 0f, w, h, p)
                glow(c, w * 0.25f, h * 0.2f, w * 0.7f, 0xFFFFFFFF.toInt(), 60)
                glow(c, w * 0.85f, h * 0.65f, w * 0.8f, 0xFFFFFFFF.toInt(), 35)
                val ln = stroke(0xFFFFFFFF.toInt(), 22, maxOf(1.5f, u * 0.8f))
                var x = -h
                while (x < w) { c.drawLine(x, h, x + h * 0.6f, 0f, ln); x += 14 * u }
            }
        }
    }
}

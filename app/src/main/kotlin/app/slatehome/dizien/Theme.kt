package app.slatehome.dizien

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.scale

/** Colour set for one theme. Amber Slate is the approved default from the mockups. */
data class Palette(
    val id: String,
    val name: String,
    val bg: Color,
    val surf: Color,
    val surf2: Color,
    val text: Color,
    val sub: Color,
    val acc: Color,
    val acc2: Color,
    val accFg: Color,
    val line: Color,
    /** Light themes use dark text on light surfaces; text drawn straight over wallpapers stays white. */
    val light: Boolean = false,
    /** Liquid Glass: translucent frosted panels over the wallpaper. */
    val glass: Boolean = false,
)

val AmberSlate = Palette(
    "amber_slate", "Amber Slate",
    bg = Color(0xFF1C1814), surf = Color(0xFF26211B), surf2 = Color(0xFF322B23),
    text = Color(0xFFF3E9D7), sub = Color(0xFFA39682),
    acc = Color(0xFFFFB020), acc2 = Color(0xFFFF7A1A), accFg = Color(0xFF1C1814), line = Color(0xFF40372C),
)
val CrimsonEmber = Palette(
    "crimson_ember", "Crimson Ember",
    bg = Color(0xFF150E11), surf = Color(0xFF22161B), surf2 = Color(0xFF2E1F26),
    text = Color(0xFFF5E8EC), sub = Color(0xFFA8929B),
    acc = Color(0xFFE0245E), acc2 = Color(0xFFFF5C8A), accFg = Color(0xFF150E11), line = Color(0xFF3D2830),
)
val CopperDusk = Palette(
    "copper_dusk", "Copper Dusk",
    bg = Color(0xFF1A130E), surf = Color(0xFF2A1E15), surf2 = Color(0xFF36281C),
    text = Color(0xFFF3E6D8), sub = Color(0xFFA8957F),
    acc = Color(0xFFFF7A1A), acc2 = Color(0xFFFFB020), accFg = Color(0xFF1A130E), line = Color(0xFF46352A),
)

private fun dark(id: String, name: String, bg: Long, surf: Long, surf2: Long, text: Long, sub: Long, acc: Long, acc2: Long, line: Long, accFg: Long = bg) =
    Palette(id, name, Color(bg), Color(surf), Color(surf2), Color(text), Color(sub), Color(acc), Color(acc2), Color(accFg), Color(line))
private fun lightP(id: String, name: String, bg: Long, surf: Long, surf2: Long, text: Long, sub: Long, acc: Long, acc2: Long, line: Long) =
    Palette(id, name, Color(bg), Color(surf), Color(surf2), Color(text), Color(sub), Color(acc), Color(acc2), Color(0xFFFFFFFF), Color(line), light = true)
val CherrySlate = dark("cherry_slate", "Cherry Slate", 0xFF18181D, 0xFF22232A, 0xFF2D2F38, 0xFFF2F0F2, 0xFF9C9AA6, 0xFFE0155B, 0xFFFF5C8D, 0xFF3A3B46, accFg = 0xFFFFFFFF)
val SlateBlue = dark("slate_blue", "Slate Blue", 0xFF11151B, 0xFF1A2029, 0xFF242C38, 0xFFE8EEF7, 0xFF8E9BAE, 0xFF5BA8FF, 0xFF8AC4FF, 0xFF2F3A4A)
val ForestTerminal = dark("forest_terminal", "Forest Green", 0xFF0D1410, 0xFF16201A, 0xFF1F2D25, 0xFFE4F3EA, 0xFF86A092, 0xFF4ADE80, 0xFF9AF0B8, 0xFF2A3D32)
val MidnightViolet = dark("midnight_violet", "Deep Violet", 0xFF130F1B, 0xFF1D1729, 0xFF282138, 0xFFEEE8F8, 0xFF9A8FB0, 0xFFB48CFF, 0xFFD3B8FF, 0xFF392F4C)
val OceanTeal = dark("ocean_teal", "Ocean Teal", 0xFF0B1618, 0xFF132326, 0xFF1C3236, 0xFFE2F4F3, 0xFF82A3A5, 0xFF2DD4BF, 0xFF7CE8D8, 0xFF25423F)
val PureBlack = dark("pure_black", "Pure Black", 0xFF000000, 0xFF0E0E0F, 0xFF1A1A1C, 0xFFF4F4F4, 0xFF9A9A9E, 0xFFFFFFFF, 0xFFC8C8CC, 0xFF2A2A2D)
val PaperAmber = lightP("paper_amber", "Paper Amber", 0xFFFBF5EA, 0xFFF2E9D8, 0xFFE8DCC6, 0xFF2A2118, 0xFF76654F, 0xFFB45F06, 0xFFE07B1A, 0xFFD9CBB2)
val SnowSlate = lightP("snow_slate", "Snow Slate", 0xFFF3F5F8, 0xFFE8ECF1, 0xFFDCE2EA, 0xFF1B2430, 0xFF5B6777, 0xFFB80F4A, 0xFFE0386E, 0xFFCBD3DE)
val MintLight = lightP("mint_light", "Mint Light", 0xFFF0F7F3, 0xFFE2EFE8, 0xFFD3E6DB, 0xFF12251C, 0xFF4F6558, 0xFF1B7F54, 0xFF2FA872, 0xFFBCD5C6)
val LiquidGlass = Palette(
    "liquid_glass", "Liquid Glass",
    bg = Color(0xFF0F1426), surf = Color(0xFF1A2138), surf2 = Color(0xFF263050),
    text = Color(0xFFF5F8FF), sub = Color(0xFFB4BED6),
    acc = Color(0xFF8FD3FF), acc2 = Color(0xFFC7A6FF), accFg = Color(0xFF0B1226), line = Color(0xFF3A4568),
    glass = true,
)
val Palettes = listOf(AmberSlate, CrimsonEmber, CopperDusk, CherrySlate, SlateBlue, ForestTerminal, MidnightViolet, OceanTeal, PureBlack, PaperAmber, SnowSlate, MintLight, LiquidGlass)
fun paletteById(id: String) = Palettes.firstOrNull { it.id == id } ?: AmberSlate

val LocalPal = compositionLocalOf { AmberSlate }

val Mono = FontFamily(
    Font(R.font.dejavu_sans_mono, FontWeight.Normal),
    Font(R.font.dejavu_sans_mono_bold, FontWeight.Bold),
)

@Composable
fun DiZienTheme(pal: Palette, content: @Composable () -> Unit) {
    val glass = remember { mutableStateOf<GlassBackdrop?>(null) }
    CompositionLocalProvider(LocalPal provides pal, LocalGlass provides glass) { content() }
}

@Composable
fun Txt(
    text: String,
    modifier: Modifier = Modifier,
    size: Float = 13f,
    color: Color = LocalPal.current.text,
    bold: Boolean = false,
    spacing: Float = 0f,
    align: TextAlign = TextAlign.Start,
    maxLines: Int = Int.MAX_VALUE,
) {
    BasicText(
        text = text,
        modifier = modifier,
        maxLines = maxLines,
        overflow = if (maxLines == Int.MAX_VALUE) TextOverflow.Clip else TextOverflow.Ellipsis,
        style = TextStyle(
            fontFamily = Mono,
            fontSize = size.sp,
            color = color,
            fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal,
            letterSpacing = spacing.sp,
            textAlign = align,
        ),
    )
}

/* ---------- line glyphs (24x24 viewBox, original paths) ---------- */

object Glyphs {
    val paths = mapOf(
        "wifi" to "M2 9a15 15 0 0 1 20 0M5 12.5a10 10 0 0 1 14 0M8.5 16a5 5 0 0 1 7 0M12 19.5v.5",
        "bluetooth" to "M7 7l10 10-5 4V3l5 4L7 17",
        "plane" to "M21 16v-2l-8-5V3.5a1.5 1.5 0 0 0-3 0V9l-8 5v2l8-2.5V19l-2 1.5V22l3.5-1 3.5 1v-1.5L13 19v-5.5z",
        "signal" to "M4 20v-4M9 20v-8M14 20V8M19 20V4",
        "location" to "M12 21s-7-6-7-11a7 7 0 0 1 14 0c0 5-7 11-7 11zM12 7.5a2.5 2.5 0 1 0 0 5a2.5 2.5 0 1 0 0-5z",
        "volume" to "M4 9v6h4l5 4V5L8 9zM16.5 8.5a5 5 0 0 1 0 7M19 6a8.5 8.5 0 0 1 0 12",
        "sun" to "M12 8a4 4 0 1 0 0 8a4 4 0 1 0 0-8zM12 2v2M12 20v2M2 12h2M20 12h2M5 5l1.5 1.5M17.5 17.5L19 19M19 5l-1.5 1.5M6.5 17.5L5 19",
        "battery" to "M3 8h15a1 1 0 0 1 1 1v6a1 1 0 0 1-1 1H3a1 1 0 0 1-1-1V9a1 1 0 0 1 1-1zM21 11v2M6 11v2",
        "bell" to "M6 17V11a6 6 0 0 1 12 0v6l2 2H4zM10 21h4",
        "close" to "M6 6l12 12M18 6L6 18",
        "term" to "M5 4h14a2 2 0 0 1 2 2v12a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2V6a2 2 0 0 1 2-2zM7 9l3 3-3 3M12 15h5",
        "file" to "M6 3h8l4 4v14H6zM14 3v4h4M9 12h6M9 16h6",
        "globe" to "M12 3a9 9 0 1 0 0 18a9 9 0 1 0 0-18zM3 12h18M12 3c3 3 3 15 0 18M12 3c-3 3-3 15 0 18",
        "box" to "M12 3l8 4v10l-8 4-8-4V7zM4 7l8 4 8-4M12 11v10",
        "gear" to "M12 9a3 3 0 1 0 0 6a3 3 0 1 0 0-6zM12 2v3M12 19v3M2 12h3M19 12h3M5 5l2 2M17 17l2 2M19 5l-2 2M7 17l-2 2",
        "palette" to "M12 3a9 9 0 1 0 0 18c1.5 0 2-1 1.5-2s0-2 1.5-2h2a3 3 0 0 0 3-3c0-5-4-11-8-11z",
        "home" to "M3 11l9-8 9 8v9a1 1 0 0 1-1 1h-5v-6H9v6H4a1 1 0 0 1-1-1z",
        "grid" to "M4 4h6v6H4zM14 4h6v6h-6zM4 14h6v6H4zM14 14h6v6h-6z",
        "search" to "M11 4a7 7 0 1 0 0 14a7 7 0 1 0 0-14zM20 20l-4-4",
        "chev" to "M9 6l6 6-6 6",
        "check" to "M5 12l5 5 9-10",
        "image" to "M5 4h14a2 2 0 0 1 2 2v12a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2V6a2 2 0 0 1 2-2zM9 8a1.5 1.5 0 1 0 0 3a1.5 1.5 0 1 0 0-3zM21 16l-5-5-9 9",
        "info" to "M12 3a9 9 0 1 0 0 18a9 9 0 1 0 0-18zM12 11v6M12 7.5v.5",
        "code" to "M8 7l-5 5 5 5M16 7l5 5-5 5",
        "scale" to "M12 3v18M5 7h14M5 7l-3 7a3 3 0 0 0 6 0zM19 7l-3 7a3 3 0 0 0 6 0z",
        "lock" to "M6 11h12v10H6zM8 11V8a4 4 0 0 1 8 0v3",
        "eye" to "M2 12s4-7 10-7 10 7 10 7-4 7-10 7S2 12 2 12zM12 9.5a2.5 2.5 0 1 0 0 5a2.5 2.5 0 1 0 0-5z",
        "trash" to "M4 7h16M9 7V4h6v3M6 7l1 14h10l1-14M10 11v6M14 11v6",
        "pin" to "M12 3l3 6 6 1-4.5 4.5 1 6.5L12 17.5 6.5 21l1-6.5L3 10l6-1z",
        "back" to "M15 6l-6 6 6 6",
        "plus" to "M12 5v14M5 12h14",
        "dock" to "M3 15h18v5H3zM7 9h10v3H7z",
    )
}

@Composable
fun GlyphIcon(name: String, color: Color, size: Dp = 22.dp, stroke: Float = 1.8f, modifier: Modifier = Modifier) {
    val path = remember(name) {
        PathParser().parsePathString(Glyphs.paths[name] ?: Glyphs.paths["box"]!!).toPath()
    }
    Canvas(modifier.size(size)) {
        val s = this.size.width / 24f
        scale(s, s, pivot = Offset.Zero) {
            drawPath(path, color, style = Stroke(width = stroke, cap = StrokeCap.Round, join = StrokeJoin.Round))
        }
    }
}

/* ---------- shared widgets ---------- */

@Composable
fun Panel(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    val p = LocalPal.current
    Column(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(p.surf)
            .border(1.dp, p.line, RoundedCornerShape(8.dp)),
    ) { content() }
}

@Composable
fun Pill(text: String, filled: Boolean, modifier: Modifier = Modifier) {
    val p = LocalPal.current
    val shape = RoundedCornerShape(50)
    Box(
        modifier
            .let { if (filled) it.clip(shape).background(p.acc) else it.glass(shape, p, p.surf2, 0.8f) }
            .border(1.dp, if (filled) p.acc else p.line, shape)
            .padding(horizontal = 12.dp, vertical = 5.dp),
    ) { Txt(text, size = 11.5f, color = if (filled) p.accFg else p.sub, bold = true, maxLines = 1) }
}

@Composable
fun Toggle(on: Boolean, onChange: (Boolean) -> Unit) {
    val p = LocalPal.current
    val shape = RoundedCornerShape(50)
    Box(
        Modifier
            .width(46.dp).height(26.dp)
            .clip(shape)
            .background(if (on) p.acc else p.surf2)
            .border(1.dp, if (on) p.acc else p.line, shape)
            .clickable { onChange(!on) }
            .padding(3.dp),
        contentAlignment = if (on) Alignment.CenterEnd else Alignment.CenterStart,
    ) {
        Box(Modifier.size(20.dp).clip(shape).background(if (on) p.accFg else p.sub))
    }
}

@Composable
fun SettingRow(
    glyph: String,
    title: String,
    sub: String? = null,
    onClick: (() -> Unit)? = null,
    trailing: @Composable () -> Unit = {},
) {
    val p = LocalPal.current
    Row(
        Modifier
            .fillMaxWidth()
            .let { if (onClick != null) it.clickable { onClick() } else it }
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(38.dp).clip(RoundedCornerShape(6.dp)).background(p.surf2),
            contentAlignment = Alignment.Center,
        ) { GlyphIcon(glyph, p.acc, 20.dp) }
        Column(Modifier.padding(start = 14.dp).weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Txt(title, size = 14.5f, bold = true)
            if (sub != null) Txt(sub, size = 12f, color = p.sub)
        }
        trailing()
    }
}

@Composable
fun RowDivider() {
    val p = LocalPal.current
    Box(Modifier.fillMaxWidth().padding(horizontal = 14.dp).height(1.dp).background(p.line))
}

@Composable
fun SectionLabel(text: String) {
    Txt(
        text.uppercase(), color = LocalPal.current.sub, size = 12f, bold = true, spacing = 1.6f,
        modifier = Modifier.padding(start = 4.dp, top = 18.dp, bottom = 8.dp),
    )
}

@Composable
fun PrimaryButton(text: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val p = LocalPal.current
    Box(
        modifier.height(48.dp).clip(RoundedCornerShape(4.dp)).background(p.acc).clickable { onClick() },
        contentAlignment = Alignment.Center,
    ) { Txt(text, size = 15f, bold = true, color = p.accFg) }
}

@Composable
fun GhostButton(text: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val p = LocalPal.current
    Box(
        modifier.height(48.dp).clip(RoundedCornerShape(4.dp)).background(p.surf2)
            .border(1.dp, p.line, RoundedCornerShape(4.dp)).clickable { onClick() },
        contentAlignment = Alignment.Center,
    ) { Txt(text, size = 14.5f, bold = true) }
}

@Composable
fun Segmented(options: List<String>, selected: Int, onSelect: (Int) -> Unit) {
    val p = LocalPal.current
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(6.dp)).background(p.surf)
            .border(1.dp, p.line, RoundedCornerShape(6.dp)).padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        options.forEachIndexed { i, label ->
            val on = i == selected
            Box(
                Modifier.weight(1f).height(38.dp).clip(RoundedCornerShape(4.dp))
                    .background(if (on) p.acc else Color.Transparent).clickable { onSelect(i) },
                contentAlignment = Alignment.Center,
            ) { Txt(label.uppercase(), size = 12.5f, bold = true, spacing = 1f, color = if (on) p.accFg else p.sub) }
        }
    }
}

/** Header block used on Look and Settings: framed glyph tile, title, subtitle, optional right-hand chip. */
@Composable
fun ScreenHeader(glyph: String, title: String, subtitle: String, right: (@Composable () -> Unit)? = null) {
    val p = LocalPal.current
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(46.dp).border(2.dp, p.acc, RoundedCornerShape(5.dp)),
            contentAlignment = Alignment.Center,
        ) { GlyphIcon(glyph, p.acc, 24.dp) }
        Column(Modifier.padding(start = 14.dp).weight(1f)) {
            Txt(title.uppercase(), size = 19f, bold = true, spacing = 1.5f)
            Txt(subtitle, size = 12.5f, color = p.sub)
        }
        right?.invoke()
    }
    Box(Modifier.fillMaxWidth().height(1.dp).background(p.line))
}

/** Bottom navigation: Home / Terminal / Look / Settings, as in the approved mockups. */
@Composable
fun NavBar(active: String, onNav: (String) -> Unit) {
    val p = LocalPal.current
    val items = listOf("home" to "Home", "term" to "Terminal", "palette" to "Look", "gear" to "Settings")
    Column(Modifier.fillMaxWidth().background(p.bg)) {
        Box(Modifier.fillMaxWidth().height(1.dp).background(p.line))
        Row(Modifier.fillMaxWidth().height(64.dp)) {
            items.forEach { (g, label) ->
                val on = label.lowercase() == active
                Column(
                    Modifier.weight(1f).height(64.dp)
                        .background(if (on) p.surf else Color.Transparent)
                        .clickable { onNav(label.lowercase()) },
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Box(Modifier.fillMaxWidth().height(3.dp).background(if (on) p.acc else Color.Transparent))
                    Box(Modifier.height(8.dp))
                    GlyphIcon(g, if (on) p.acc else p.sub, 20.dp)
                    Txt(label.uppercase(), size = 9.5f, bold = true, spacing = 0.9f, color = if (on) p.acc else p.sub,
                        modifier = Modifier.padding(top = 3.dp))
                }
            }
        }
    }
}

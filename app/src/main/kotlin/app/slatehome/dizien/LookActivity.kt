package app.slatehome.dizien

import android.graphics.Color as AColor
import android.os.Bundle
import android.net.Uri
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

class LookActivity : ComponentActivity() {
    private lateinit var store: Store
    private var wallVersion by mutableLongStateOf(0L)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(SystemBarStyle.dark(AColor.TRANSPARENT), SystemBarStyle.dark(AColor.TRANSPARENT))
        if (android.os.Build.VERSION.SDK_INT >= 29) window.isNavigationBarContrastEnforced = false
        store = Store.get(this)
        wallVersion = Wallpapers.customFile(this).lastModified()
        setContent {
            DiZienTheme(paletteById(store.look.themeId)) {
                LookScreen(store, wallVersion, { wallVersion = Wallpapers.customFile(this).lastModified() }, this)
            }
        }
    }
}

@Composable
fun LookScreen(store: Store, wallVersion: Long, onCustomChanged: () -> Unit, act: ComponentActivity) {
    val pal = LocalPal.current
    SystemBarIcons(pal.light)
    val ctx = LocalContext.current
    var tab by remember { mutableIntStateOf(0) }
    var draft by remember { mutableStateOf(store.look) }
    var draftSystem by remember { mutableStateOf(store.alsoSystemWallpaper) }
    var preview by remember { mutableStateOf(false) }
    var sheet by remember { mutableStateOf<SheetModel?>(null) }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        if (uri != null) {
            if (Wallpapers.importCustom(ctx, uri)) {
                onCustomChanged()
                draft = draft.copy(wallpaper = WALL_CUSTOM)
                toast(ctx, "Image added")
            } else toast(ctx, "Could not read that image")
        }
    }

    Box(Modifier.fillMaxSize().background(pal.bg)) {
        Column(Modifier.fillMaxSize().statusBarsPadding()) {
            ScreenHeader("palette", "Look", if (tab == 2) "Original wallpapers" else if (tab == 1) "Icon style" else "Theme · icon style") {
                Pill("Preview", false, Modifier.clickable { preview = true })
            }
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(16.dp)) {
                Segmented(listOf("Theme", "Icons", "Wallpaper"), tab) { tab = it }
                Box(Modifier.height(14.dp))
                when (tab) {
                    0 -> {
                        ThemeTab(draft) { draft = it }
                        SectionLabel("Icon style")
                        IconStyleList(draft, pal) { draft = it }
                    }
                    1 -> {
                        IconStyleList(draft, pal) { draft = it }
                        Box(Modifier.height(14.dp))
                        Panel {
                            SettingRow("palette", "Theme tint", "Amber glyphs for apps with themed icons (Android 13+)") {
                                Toggle(draft.themeTint) { draft = draft.copy(themeTint = it) }
                            }
                        }
                        Box(Modifier.height(12.dp))
                        Txt(
                            "Icons are drawn by the launcher from your installed apps. The small line symbols (Terminal, Look, Launcher) are original to this project; no third-party icon pack is bundled.",
                            size = 11.5f, color = pal.sub,
                        )
                    }
                    else -> WallpaperTab(draft, draftSystem, { draft = it }, { draftSystem = it }, { picker.launch("image/*") }, wallVersion, store)
                }
                Box(Modifier.height(16.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    GhostButton(if (tab == 2) "Preview" else "Reset", Modifier.weight(1f)) {
                        if (tab == 2) preview = true else draft = draft.copy(themeId = "amber_slate", iconStyle = IconStyle.LINE, themeTint = true)
                    }
                    PrimaryButton(if (tab == 2) "Apply wallpaper" else "Apply look", Modifier.weight(1.2f)) {
                        if (tab == 2) {
                            store.applyWallpaper(draft.wallpaper, draftSystem)
                            if (draftSystem) applySystemWallpaper(ctx, draft.wallpaper)
                            toast(ctx, "Wallpaper applied")
                        } else {
                            store.applyLook(store.look.copy(themeId = draft.themeId, iconStyle = draft.iconStyle, themeTint = draft.themeTint))
                            if (draft.themeId == "liquid_glass" && draft.wallpaper in GlassWalls.FIRST..GlassWalls.LAST) store.applyWallpaper(draft.wallpaper, false)
                            toast(ctx, "Look applied")
                        }
                    }
                }
                Box(Modifier.height(16.dp))
            }
            NavBar("look") { d -> navigate(act, d, "look") { sheet = SheetModel("Terminal app not linked", NO_TERMINAL_TEXT, listOf(SheetAction("grid", "Open launcher settings") { act.startActivity(android.content.Intent(act, SettingsActivity::class.java).putExtra(SettingsActivity.EXTRA_NO_TERMINAL, true)); act.finish() })) } }
            Box(Modifier.navigationBarsPadding())
        }

        if (preview) PreviewOverlay(draft, wallVersion, tab == 2) { preview = false }
        sheet?.let { s -> ActionSheet(s.title, s.subtitle, s.actions) { sheet = null } }
    }
    BackHandler(enabled = preview) { preview = false }
}

@Composable
private fun ThemeTab(draft: LookState, onChange: (LookState) -> Unit) {
    val pal = LocalPal.current
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        GhostButton("Liquid Glass preset", Modifier.fillMaxWidth()) {
            onChange(draft.copy(themeId = "liquid_glass", iconStyle = IconStyle.GLASS, wallpaper = GlassWalls.FIRST))
        }
        Palettes.chunked(3).forEach { rowT ->
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                rowT.forEach { t ->
                    val sel = draft.themeId == t.id
                    Column(
                        Modifier.weight(1f).clip(RoundedCornerShape(8.dp)).background(pal.surf)
                            .border(if (sel) 2.dp else 1.dp, if (sel) pal.acc else pal.line, RoundedCornerShape(8.dp))
                            .clickable { onChange(draft.copy(themeId = t.id)) }.padding(8.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Column(Modifier.fillMaxWidth().height(84.dp).clip(RoundedCornerShape(6.dp)).background(t.bg).border(1.dp, t.line, RoundedCornerShape(6.dp)).padding(9.dp)) {
                            Box(Modifier.fillMaxWidth(0.62f).height(8.dp).background(t.acc))
                            Box(Modifier.height(7.dp))
                            Box(Modifier.fillMaxWidth(0.9f).height(7.dp).background(t.surf2))
                            Box(Modifier.height(4.dp))
                            Box(Modifier.fillMaxWidth(0.7f).height(7.dp).background(t.surf2))
                            Box(Modifier.weight(1f))
                            Box(Modifier.fillMaxWidth().height(12.dp).background(t.acc2))
                        }
                        Txt(t.name, size = 11f, bold = sel, color = if (sel) pal.text else pal.sub,
                            modifier = Modifier.padding(top = 7.dp, bottom = 1.dp), align = TextAlign.Center, maxLines = 1)
                        Txt(if (t.light) "light" else "dark", size = 9.5f, color = pal.sub.copy(alpha = 0.8f), align = TextAlign.Center)
                    }
                }
                repeat(3 - rowT.size) { Box(Modifier.weight(1f)) }
            }
        }
    }
}

private val styleNames = listOf(
    "Amber Line" to "outline tile", "Solid Tile" to "filled accent", "Round Glow" to "circle + gradient",
    "Soft Slate" to "quiet tile", "Gradient Tile" to "glossy square", "Ring" to "circle outline",
    "Hex Cell" to "hexagon", "Leaf" to "leaf corners", "Bare" to "no tile, big glyph",
    "Liquid Glass" to "glossy glass tile",
)

@Composable
private fun IconStyleList(draft: LookState, pal: Palette, onChange: (LookState) -> Unit) {
    val previewPal = paletteById(draft.themeId)
    val glyphs = listOf("term", "file", "gear")
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        styleNames.withIndex().chunked(2).forEach { pair ->
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                pair.forEach { (i, nd) ->
                    val sel = draft.iconStyle == i
                    Column(
                        Modifier.weight(1f).clip(RoundedCornerShape(8.dp)).background(pal.surf)
                            .border(if (sel) 2.dp else 1.dp, if (sel) pal.acc else pal.line, RoundedCornerShape(8.dp))
                            .clickable { onChange(draft.copy(iconStyle = i)) }.padding(10.dp),
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Txt(nd.first, size = 13f, bold = true, maxLines = 1, modifier = Modifier.weight(1f))
                            if (sel) GlyphIcon("check", pal.acc, 16.dp, 2.4f)
                        }
                        Txt(nd.second, size = 10.5f, color = pal.sub, maxLines = 1, modifier = Modifier.padding(top = 2.dp))
                        Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                            glyphs.forEach { g ->
                                Image(rememberGlyphTile(g, i, previewPal, 44f), null, Modifier.size(44.dp))
                            }
                        }
                    }
                }
                if (pair.size < 2) Box(Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun WallpaperTab(
    draft: LookState, system: Boolean, onChange: (LookState) -> Unit, onSystem: (Boolean) -> Unit,
    pickOwn: () -> Unit, wallVersion: Long, store: Store,
) {
    val pal = LocalPal.current
    val hasCustom = Wallpapers.customFile(LocalContext.current).exists()
    val all = Wallpapers.names.indices.toList() + if (hasCustom) listOf(WALL_CUSTOM) else emptyList()
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        all.chunked(3).forEach { rowIds ->
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                rowIds.forEach { id ->
                    val sel = draft.wallpaper == id
                    Column(Modifier.weight(1f).clickable { onChange(draft.copy(wallpaper = id)) }) {
                        Box(
                            Modifier.fillMaxWidth().aspectRatio(0.62f).clip(RoundedCornerShape(8.dp))
                                .border(if (sel) 3.dp else 1.dp, if (sel) pal.acc else pal.line, RoundedCornerShape(8.dp)),
                        ) {
                            WallpaperBackground(id, Modifier.fillMaxSize(), wallVersion)
                            if (sel) Box(
                                Modifier.align(Alignment.TopEnd).padding(6.dp).size(24.dp).clip(RoundedCornerShape(4.dp)).background(pal.acc),
                                contentAlignment = Alignment.Center,
                            ) { GlyphIcon("check", pal.accFg, 16.dp, 2.4f) }
                        }
                        Txt(if (id == WALL_CUSTOM) "My image" else Wallpapers.names[id], size = 11.5f, color = if (sel) pal.text else pal.sub,
                            align = TextAlign.Center, maxLines = 1, modifier = Modifier.fillMaxWidth().padding(top = 6.dp))
                    }
                }
                repeat(3 - rowIds.size) { Box(Modifier.weight(1f)) }
            }
        }
        Panel {
            SettingRow("image", "Use my own image", "Pick from gallery", onClick = pickOwn) { GlyphIcon("chev", pal.sub, 20.dp) }
            RowDivider()
            SettingRow("dock", "Also set Android wallpaper", "Lock screen and other launchers") { Toggle(system, onSystem) }
        }
        Txt(
            "Built-in wallpapers are original artwork drawn by this app, released under GPL-3.0. Your own image stays on this device.",
            size = 11.5f, color = pal.sub,
        )
    }
}

@Composable
private fun PreviewOverlay(draft: LookState, wallVersion: Long, wallOnly: Boolean, onClose: () -> Unit) {
    val pal = paletteById(draft.themeId)
    val sample = listOf(
        LItem("p1", "Terminal", null, "term"), LItem("p2", "Files", null, "file"), LItem("p3", "Browser", null, "globe"),
        LItem("p4", "Software", null, "box"), LItem("p5", "Look", null, "palette"), LItem("p6", "Launcher", null, "gear"),
        LItem("p7", "Home", null, "home"), LItem("p8", "Gallery", null, "image"),
    )
    Box(Modifier.fillMaxSize().clickable { onClose() }) {
        WallpaperBackground(draft.wallpaper, Modifier.fillMaxSize(), wallVersion)
        Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(20.dp)) {
            Txt("09:41", size = 60f, bold = true, color = pal.text, modifier = Modifier.padding(top = 16.dp))
            Txt("PREVIEW · TAP TO CLOSE", size = 12f, color = pal.text.copy(alpha = 0.85f), spacing = 1.2f)
            Box(Modifier.height(36.dp))
            sample.chunked(4).forEach { r ->
                Row(Modifier.fillMaxWidth().padding(bottom = 14.dp)) {
                    r.forEach { it ->
                        Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                            Image(rememberGlyphTile(it.glyph!!, draft.iconStyle, pal, 56f), null, Modifier.size(56.dp))
                            Txt(it.label, size = 11f, color = pal.text, modifier = Modifier.padding(top = 4.dp), maxLines = 1)
                        }
                    }
                }
            }
        }
    }
}

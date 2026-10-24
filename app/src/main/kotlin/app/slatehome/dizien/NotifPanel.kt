package app.slatehome.dizien

import android.content.Context
import android.content.Intent
import android.net.wifi.WifiManager
import android.provider.Settings
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private class QTile(val glyph: String, val label: String, val action: String, val on: Boolean?)

private fun quickTiles(ctx: Context): List<QTile> {
    val wifi = try {
        (ctx.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager).isWifiEnabled
    } catch (_: Throwable) { null }
    val plane = try { Settings.Global.getInt(ctx.contentResolver, Settings.Global.AIRPLANE_MODE_ON, 0) == 1 } catch (_: Throwable) { null }
    return listOf(
        QTile("wifi", "Wi-Fi", Settings.ACTION_WIFI_SETTINGS, wifi),
        QTile("bluetooth", "Bluetooth", Settings.ACTION_BLUETOOTH_SETTINGS, null),
        QTile("plane", "Airplane", Settings.ACTION_AIRPLANE_MODE_SETTINGS, plane),
        QTile("signal", "Mobile", Settings.ACTION_NETWORK_OPERATOR_SETTINGS, null),
        QTile("location", "Location", Settings.ACTION_LOCATION_SOURCE_SETTINGS, null),
        QTile("volume", "Sound", Settings.ACTION_SOUND_SETTINGS, null),
        QTile("sun", "Display", Settings.ACTION_DISPLAY_SETTINGS, null),
        QTile("battery", "Battery", Settings.ACTION_BATTERY_SAVER_SETTINGS, null),
    )
}

fun openSystemSettings(ctx: Context, action: String) {
    try { ctx.startActivity(Intent(action).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
    catch (_: Throwable) {
        try { ctx.startActivity(Intent(Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) } catch (_: Throwable) { }
    }
}

/** Opens Android's own "Notification access" screen. */
fun openNotificationAccessSettings(ctx: Context) = openSystemSettings(ctx, "android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS")

/**
 * Amber Slate notification panel that slides down over Home. Quick tiles open the matching Android
 * settings screen; notifications appear only after notification access is enabled in Settings.
 */
@Composable
fun NotificationPanel(
    open: Boolean, pal: Palette, timeStr: String, dateStr: String,
    onClose: () -> Unit, onAndroidShade: () -> Unit, onLauncherSettings: () -> Unit,
) {
    val ctx = LocalContext.current
    AnimatedVisibility(open, enter = fadeIn() + slideInVertically { -it / 3 }, exit = fadeOut() + slideOutVertically { -it / 3 }) {
        val granted = remember { NotifState.isAccessGranted(ctx) }
        val tiles = remember { quickTiles(ctx) }
        val shape = RoundedCornerShape(bottomStart = 18.dp, bottomEnd = 18.dp)
        Box(
            Modifier.fillMaxSize().background(Color(0x99000000))
                .clickable(remember { MutableInteractionSource() }, null) { onClose() },
        ) {
            Column(
                Modifier.align(Alignment.TopCenter).fillMaxWidth().fillMaxHeight(0.88f)
                    .glass(shape, pal, pal.bg, 1.2f).border(1.dp, pal.line, shape)
                    .clickable(remember { MutableInteractionSource() }, null) { }
                    .pointerInput(Unit) {
                        var total = 0f
                        detectVerticalDragGestures(
                            onDragStart = { total = 0f },
                            onDragEnd = { if (total < -80f) onClose() },
                            onDragCancel = { total = 0f },
                            onVerticalDrag = { _, dy -> total += dy },
                        )
                    }
                    .statusBarsPadding(),
            ) {
                // Header
                Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 12.dp, top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Txt(timeStr, size = 34f, bold = true, color = pal.text)
                        Txt(dateStr, size = 11.5f, color = pal.sub, spacing = 1.1f)
                    }
                    Box(
                        Modifier.clip(RoundedCornerShape(50)).border(1.dp, pal.line, RoundedCornerShape(50))
                            .clickable { onAndroidShade() }.padding(horizontal = 12.dp, vertical = 7.dp),
                    ) { Txt("Android shade", size = 11f, color = pal.sub) }
                    Box(Modifier.padding(start = 4.dp).size(44.dp).clip(RoundedCornerShape(8.dp)).clickable { onLauncherSettings() }, contentAlignment = Alignment.Center) {
                        GlyphIcon("gear", pal.acc, 22.dp)
                    }
                }
                // Quick tiles (open the matching system settings)
                Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    tiles.chunked(4).forEach { rowTiles ->
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            rowTiles.forEach { t ->
                                val on = t.on == true
                                val shapeT = RoundedCornerShape(8.dp)
                                Column(
                                    Modifier.weight(1f).height(64.dp).clip(shapeT)
                                        .background(if (on) pal.acc else pal.surf)
                                        .border(1.dp, if (on) pal.acc else pal.line, shapeT)
                                        .clickable { openSystemSettings(ctx, t.action); onClose() },
                                    horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center,
                                ) {
                                    GlyphIcon(t.glyph, if (on) pal.accFg else pal.acc, 22.dp)
                                    Txt(t.label, size = 10.5f, color = if (on) pal.accFg else pal.text, bold = on, modifier = Modifier.padding(top = 4.dp), maxLines = 1)
                                }
                            }
                        }
                    }
                }
                Box(Modifier.fillMaxWidth().height(1.dp).background(pal.line))
                // Notifications
                val items = NotifState.items
                Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 12.dp, top = 10.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Txt(
                        if (granted && NotifState.connected) "NOTIFICATIONS (${items.size})" else "NOTIFICATIONS",
                        size = 11.5f, bold = true, spacing = 1.5f, color = pal.sub, modifier = Modifier.weight(1f),
                    )
                    if (granted && items.any { it.clearable }) Box(
                        Modifier.clip(RoundedCornerShape(50)).clickable { NotifState.dismissAll() }.padding(horizontal = 12.dp, vertical = 6.dp),
                    ) { Txt("Clear all", size = 12f, bold = true, color = pal.acc) }
                }
                Box(Modifier.weight(1f).fillMaxWidth()) {
                    when {
                        !granted -> Column(
                            Modifier.fillMaxSize().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center,
                        ) {
                            GlyphIcon("bell", pal.sub, 30.dp)
                            Txt("Show notifications here", size = 15f, bold = true, modifier = Modifier.padding(top = 12.dp), align = TextAlign.Center)
                            Txt(
                                "Turn on notification access to read them in this panel. They stay on this phone: the launcher has no internet permission.",
                                size = 12f, color = pal.sub, align = TextAlign.Center, modifier = Modifier.padding(top = 6.dp),
                            )
                            Box(
                                Modifier.padding(top = 14.dp).clip(RoundedCornerShape(50)).background(pal.acc)
                                    .clickable { onLauncherSettings() }.padding(horizontal = 18.dp, vertical = 9.dp),
                            ) { Txt("Set up in Settings", size = 12.5f, bold = true, color = pal.accFg) }
                        }
                        items.isEmpty() -> Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                            GlyphIcon("bell", pal.sub, 30.dp)
                            Txt("No notifications", size = 14f, color = pal.sub, modifier = Modifier.padding(top = 10.dp))
                        }
                        else -> LazyColumn(Modifier.fillMaxSize().padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            items(items.toList(), key = { it.key }) { n -> NotifCard(n, pal, onClose) }
                            item { Box(Modifier.height(10.dp)) }
                        }
                    }
                }
                // Grab handle
                Box(Modifier.fillMaxWidth().padding(vertical = 10.dp), contentAlignment = Alignment.Center) {
                    Box(Modifier.size(width = 44.dp, height = 4.dp).clip(RoundedCornerShape(50)).background(pal.line))
                }
            }
        }
    }
}

@Composable
private fun NotifCard(n: NItem, pal: Palette, onClose: () -> Unit) {
    val shape = RoundedCornerShape(8.dp)
    val time = remember(n.time) { SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(n.time)) }
    Row(
        Modifier.fillMaxWidth().glass(shape, pal, pal.surf, 0.8f).border(1.dp, pal.line, shape)
            .clickable {
                try { n.intent?.send() } catch (_: Throwable) { }
                if (n.autoCancel && n.clearable) NotifState.dismiss(n.key)
                onClose()
            }
            .padding(start = 12.dp, top = 10.dp, bottom = 10.dp, end = 4.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Box(Modifier.padding(top = 2.dp).size(26.dp).clip(RoundedCornerShape(6.dp)).background(pal.surf2), contentAlignment = Alignment.Center) {
            if (n.icon != null) Image(n.icon.asImageBitmap(), null, Modifier.size(16.dp), colorFilter = ColorFilter.tint(pal.acc))
            else GlyphIcon("bell", pal.acc, 16.dp)
        }
        Column(Modifier.padding(start = 12.dp).weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Row {
                Txt(n.app, size = 11f, color = pal.acc, bold = true, maxLines = 1, modifier = Modifier.weight(1f, fill = false))
                Txt("  ·  $time", size = 11f, color = pal.sub)
            }
            if (n.title.isNotBlank()) Txt(n.title, size = 13.5f, bold = true, maxLines = 1)
            if (n.text.isNotBlank()) Txt(n.text, size = 12f, color = pal.sub, maxLines = 2)
        }
        if (n.clearable) Box(Modifier.size(40.dp).clip(RoundedCornerShape(8.dp)).clickable { NotifState.dismiss(n.key) }, contentAlignment = Alignment.Center) {
            GlyphIcon("close", pal.sub, 18.dp, 2f)
        }
    }
}

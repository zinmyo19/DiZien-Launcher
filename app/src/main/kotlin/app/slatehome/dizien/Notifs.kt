package app.slatehome.dizien

import android.app.Notification
import android.app.PendingIntent
import android.content.ComponentName
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.Handler
import android.os.Looper
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.app.NotificationManagerCompat

/** One notification as shown in the in-launcher panel. Kept in memory only, never stored or sent anywhere. */
class NItem(
    val key: String, val pkg: String, val app: String, val title: String, val text: String,
    val time: Long, val clearable: Boolean, val autoCancel: Boolean,
    val icon: Bitmap?, val intent: PendingIntent?,
)

object NotifState {
    val items = mutableStateListOf<NItem>()
    var connected by mutableStateOf(false)
    @Volatile var service: DZNotificationListener? = null

    /** True when the owner has switched notification access on for this app in Android settings. */
    fun isAccessGranted(ctx: Context): Boolean =
        NotificationManagerCompat.getEnabledListenerPackages(ctx).contains(ctx.packageName)

    /** Asks Android to reconnect the listener if access is on but the service is not bound yet. */
    fun ensureBound(ctx: Context) {
        if (isAccessGranted(ctx) && !connected) {
            try { NotificationListenerService.requestRebind(ComponentName(ctx, DZNotificationListener::class.java)) }
            catch (_: Throwable) { }
        }
    }

    fun dismiss(key: String) { try { service?.cancelNotification(key) } catch (_: Throwable) { } }
    fun dismissAll() { try { service?.cancelAllNotifications() } catch (_: Throwable) { } }
}

/**
 * Reads the notifications that are currently posted so the launcher can show them in its own panel.
 * It only runs after the owner enables "Notification access" for this app in Android settings.
 * The launcher has no INTERNET permission, so nothing read here can leave the device.
 */
class DZNotificationListener : NotificationListenerService() {
    private val main = Handler(Looper.getMainLooper())

    override fun onListenerConnected() {
        NotifState.service = this
        NotifState.connected = true
        refresh()
    }

    override fun onListenerDisconnected() {
        main.post {
            NotifState.connected = false
            NotifState.service = null
            NotifState.items.clear()
        }
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) = refresh()
    override fun onNotificationRemoved(sbn: StatusBarNotification?) = refresh()

    private fun refresh() {
        val list = try { activeNotifications?.toList() ?: emptyList() } catch (_: Throwable) { emptyList() }
        val out = list.mapNotNull { toItem(it) }.sortedByDescending { it.time }
        main.post { NotifState.items.clear(); NotifState.items.addAll(out) }
    }

    private fun toItem(sbn: StatusBarNotification): NItem? {
        val n = sbn.notification ?: return null
        if (n.flags and Notification.FLAG_GROUP_SUMMARY != 0) return null
        val ex = n.extras
        val title = ex.getCharSequence(Notification.EXTRA_TITLE)?.toString().orEmpty()
        val text = (ex.getCharSequence(Notification.EXTRA_TEXT) ?: ex.getCharSequence(Notification.EXTRA_BIG_TEXT))?.toString().orEmpty()
        if (title.isBlank() && text.isBlank()) return null
        val label = try {
            packageManager.getApplicationLabel(packageManager.getApplicationInfo(sbn.packageName, 0)).toString()
        } catch (_: Throwable) { ex.getString("android.substName") ?: sbn.packageName.substringAfterLast('.') }
        val icon = try {
            n.smallIcon?.loadDrawable(this)?.let { d ->
                val bmp = Bitmap.createBitmap(48, 48, Bitmap.Config.ARGB_8888)
                d.setBounds(0, 0, 48, 48); d.draw(Canvas(bmp)); bmp
            }
        } catch (_: Throwable) { null }
        val ongoing = n.flags and (Notification.FLAG_ONGOING_EVENT or Notification.FLAG_NO_CLEAR) != 0
        return NItem(
            sbn.key, sbn.packageName, label, title, text, if (sbn.postTime > 0) sbn.postTime else n.`when`,
            clearable = !ongoing, autoCancel = n.flags and Notification.FLAG_AUTO_CANCEL != 0,
            icon = icon, intent = n.contentIntent,
        )
    }
}

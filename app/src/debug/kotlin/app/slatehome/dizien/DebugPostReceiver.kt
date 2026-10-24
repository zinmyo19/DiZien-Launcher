package app.slatehome.dizien

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build

class DebugPostReceiver : BroadcastReceiver() {
    override fun onReceive(c: Context, i: Intent) {
        val nm = c.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val ch = "dbg"
        if (Build.VERSION.SDK_INT >= 26) nm.createNotificationChannel(NotificationChannel(ch, "Debug", NotificationManager.IMPORTANCE_DEFAULT))
        val pi = PendingIntent.getActivity(c, 0, Intent(c, SettingsActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
        val data = listOf(
            Triple("Build finished", "DZTerminal alpine rootfs is ready to test", false),
            Triple("Mom", "Are you coming for dinner tonight? We are making mohinga with extra fish cake and fried beans.", false),
            Triple("Battery saver", "Turned on at 15 percent", true),
        )
        data.forEachIndexed { n, (t, x, ongoing) ->
            val b = if (Build.VERSION.SDK_INT >= 26) Notification.Builder(c, ch) else Notification.Builder(c)
            b.setSmallIcon(android.R.drawable.ic_dialog_email).setContentTitle(t).setContentText(x).setContentIntent(pi).setAutoCancel(true).setOngoing(ongoing)
            nm.notify(100 + n, b.build())
        }
    }
}

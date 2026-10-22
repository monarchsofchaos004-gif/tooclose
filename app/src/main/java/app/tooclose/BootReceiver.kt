package app.tooclose

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * After a reboot Android does not let a background service open the camera, so instead of starting
 * silently we post a reminder; tapping it opens the app, which restarts the guard.
 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(c: Context, i: Intent) {
        if (i.action != Intent.ACTION_BOOT_COMPLETED || !Prefs.wanted(c)) return
        val nm = c.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.createNotificationChannel(NotificationChannel(CH, c.getString(R.string.notif_boot_channel), NotificationManager.IMPORTANCE_DEFAULT))
        val pi = PendingIntent.getActivity(c, 1, Intent(c, MainActivity::class.java).putExtra(MainActivity.EXTRA_START, true),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val n = Notification.Builder(c, CH)
            .setSmallIcon(R.drawable.ic_notif)
            .setContentTitle(c.getString(R.string.app_name))
            .setContentText(c.getString(R.string.notif_boot))
            .setContentIntent(pi)
            .setAutoCancel(true)
            .build()
        try { nm.notify(2, n) } catch (e: SecurityException) {}
    }

    companion object { const val CH = "boot" }
}

package dev.vanta.smartlock

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat

/**
 * Device-local notifications for incoming VANTA messages.
 * This class performs no network access and never includes message text in
 * the notification, preserving privacy on the lock screen.
 */
object VantaNotifications {
    private const val CHANNEL_ID = "vanta_messages"
    private const val CHANNEL_NAME = "VANTA messages"

    fun createChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, CHANNEL_NAME, NotificationManager.IMPORTANCE_DEFAULT).apply {
                    description = "Notifications for messages received over offline VANTA connections"
                    setShowBadge(true)
                }
            )
        }
    }

    fun showMessage(context: Context, peerId: String) {
        val launch = Intent(context, MeshChatActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val pending = PendingIntent.getActivity(
            context, 0, launch,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_notify_chat)
            .setContentTitle("New secure VANTA message")
            .setContentText("Open VANTA MESH to view your message")
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .setContentIntent(pending)
            .build()
        try {
            NotificationManagerCompat.from(context).notify(peerId.hashCode(), notification)
        } catch (_: SecurityException) {
            // Android 13+ notification permission may be denied by the user.
        }
    }
}

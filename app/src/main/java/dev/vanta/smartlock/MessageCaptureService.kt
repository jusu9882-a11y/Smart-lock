package dev.vanta.smartlock

import android.app.Notification
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import java.util.UUID

class MessageCaptureService : NotificationListenerService() {
    override fun onNotificationPosted(sbn: StatusBarNotification) {
        if (sbn.packageName != "com.whatsapp" && sbn.packageName != "com.whatsapp.w4b") return
        val notification = sbn.notification ?: return
        val extras = notification.extras ?: return
        val sender = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString()?.trim().orEmpty()
        val text = (extras.getCharSequence(Notification.EXTRA_BIG_TEXT)
            ?: extras.getCharSequence(Notification.EXTRA_TEXT))?.toString()?.trim().orEmpty()
        if (text.isBlank() || text == "‎" || text.equals("1 new message", true)) return
        val kind = if (text.contains("voice message", true) ||
            text.contains("audio", true) || text.contains("voice note", true)) "VOICE NOTICE" else "TEXT"
        VaultStore.add(this, VaultMessage(UUID.randomUUID().toString(),
            sbn.postTime, sender.ifBlank { "WhatsApp" }, text, kind))
    }
}

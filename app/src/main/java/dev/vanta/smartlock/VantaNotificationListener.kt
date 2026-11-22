package dev.vanta.smartlock

import android.app.Notification
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification

class VantaNotificationListener : NotificationListenerService() {
    override fun onNotificationPosted(sbn: StatusBarNotification) {
        val isWhatsApp = sbn.packageName == WHATSAPP_PACKAGE || sbn.packageName == WHATSAPP_BUSINESS_PACKAGE
        if (!isWhatsApp) return

        val extras = sbn.notification.extras ?: return
        val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString()?.trim().orEmpty()
        val body = (extras.getCharSequence(Notification.EXTRA_BIG_TEXT)
            ?: extras.getCharSequence(Notification.EXTRA_TEXT)
            ?: extras.getCharSequence(Notification.EXTRA_SUB_TEXT))?.toString()?.trim().orEmpty()
        if (title.isBlank() && body.isBlank()) return

        val appName = if (sbn.packageName == WHATSAPP_BUSINESS_PACKAGE) "WhatsApp Business" else "WhatsApp"
        MessageVaultStore.save(applicationContext, VaultMessage(sbn.packageName, appName, title, body, sbn.postTime))
    }

    companion object {
        private const val WHATSAPP_PACKAGE = "com.whatsapp"
        private const val WHATSAPP_BUSINESS_PACKAGE = "com.whatsapp.w4b"
    }
}

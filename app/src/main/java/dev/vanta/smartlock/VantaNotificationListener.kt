package dev.vanta.smartlock
import android.app.Notification
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
class VantaNotificationListener:NotificationListenerService(){
 override fun onNotificationPosted(sbn:StatusBarNotification){
  if(sbn.packageName==packageName)return
  val e=sbn.notification.extras?:return
  val title=e.getCharSequence(Notification.EXTRA_TITLE)?.toString()?.trim().orEmpty()
  val body=(e.getCharSequence(Notification.EXTRA_BIG_TEXT)?:e.getCharSequence(Notification.EXTRA_TEXT)?:e.getCharSequence(Notification.EXTRA_SUB_TEXT))?.toString()?.trim().orEmpty()
  if(title.isBlank()&&body.isBlank())return
  val app=try{packageManager.getApplicationLabel(packageManager.getApplicationInfo(sbn.packageName,0)).toString()}catch(_:Exception){sbn.packageName}
  MessageVaultStore.save(applicationContext,VaultMessage(app,title,body,sbn.postTime))
 }
}
package dev.vanta.smartlock
import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
data class VaultMessage(val app:String,val title:String,val body:String,val receivedAt:Long)
object MessageVaultStore {
 private const val PREFS="void_message_vault"; private const val KEY="messages"; private const val MAX=300
 @Synchronized fun save(context:Context,message:VaultMessage) {
  val prefs=context.getSharedPreferences(PREFS,Context.MODE_PRIVATE); val old=JSONArray(prefs.getString(KEY,"[]")?:"[]"); val next=JSONArray()
  next.put(JSONObject().apply { put("app",message.app);put("title",message.title);put("body",message.body);put("receivedAt",message.receivedAt) })
  for(i in 0 until minOf(old.length(),MAX-1)) next.put(old.getJSONObject(i))
  prefs.edit().putString(KEY,next.toString()).apply()
 }
 @Synchronized fun read(context:Context):List<VaultMessage> {
  val raw=context.getSharedPreferences(PREFS,Context.MODE_PRIVATE).getString(KEY,"[]")?:"[]"
  return try { val a=JSONArray(raw);(0 until a.length()).map { i-> val o=a.getJSONObject(i);VaultMessage(o.optString("app"),o.optString("title"),o.optString("body"),o.optLong("receivedAt")) } } catch(_:Exception){ emptyList() }
 }
 fun clear(context:Context){context.getSharedPreferences(PREFS,Context.MODE_PRIVATE).edit().remove(KEY).apply()}
}
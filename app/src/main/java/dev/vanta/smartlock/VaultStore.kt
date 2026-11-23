package dev.vanta.smartlock

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import org.json.JSONArray
import org.json.JSONObject
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

data class VaultMessage(
    val id: String,
    val timestamp: Long,
    val sender: String,
    val content: String,
    val kind: String,
    val sourceKey: String = id
)

object VaultStore {
    private const val PREFS = "void_message_vault"
    private const val DATA = "encrypted_messages"
    private const val KEY_ALIAS = "vanta_message_vault_key"

    @Synchronized fun add(context: Context, message: VaultMessage) {
        val all = load(context).toMutableList()
        // A notification can be posted repeatedly as WhatsApp updates its preview.
        // Keep one current entry per Android notification key.
        all.removeAll { it.sourceKey == message.sourceKey }
        all.add(0, message)
        save(context, all.take(300))
    }

    @Synchronized fun load(context: Context): List<VaultMessage> {
        val encoded = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(DATA, null)
            ?: return emptyList()
        return try {
            val packed = Base64.decode(encoded, Base64.NO_WRAP)
            require(packed.size > 12) { "Encrypted vault data is incomplete." }
            val iv = packed.copyOfRange(0, 12)
            val cipherText = packed.copyOfRange(12, packed.size)
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, getKey(), GCMParameterSpec(128, iv))
            val array = JSONArray(String(cipher.doFinal(cipherText), Charsets.UTF_8))
            (0 until array.length()).map { i ->
                val item = array.getJSONObject(i)
                val id = item.getString("id")
                VaultMessage(
                    id,
                    item.getLong("timestamp"),
                    item.getString("sender"),
                    item.getString("content"),
                    item.getString("kind"),
                    item.optString("sourceKey", id)
                )
            }
        } catch (_: Exception) { emptyList() }
    }

    @Synchronized fun clear(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().remove(DATA).apply()
    }

    private fun save(context: Context, messages: List<VaultMessage>) {
        val array = JSONArray()
        messages.forEach { m ->
            array.put(JSONObject().put("id", m.id).put("timestamp", m.timestamp)
                .put("sender", m.sender).put("content", m.content).put("kind", m.kind)
                .put("sourceKey", m.sourceKey))
        }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, getKey())
        val encrypted = cipher.doFinal(array.toString().toByteArray(Charsets.UTF_8))
        val packed = cipher.iv + encrypted
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString(DATA, Base64.encodeToString(packed, Base64.NO_WRAP)).apply()
    }

    private fun getKey(): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (store.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        generator.init(KeyGenParameterSpec.Builder(KEY_ALIAS,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setRandomizedEncryptionRequired(true)
            .build())
        return generator.generateKey()
    }
}

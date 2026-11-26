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

internal data class StoredChatMessage(
    val sender: String,
    val content: String,
    val timestamp: Long
)

/** Encrypted, bounded, app-private chat history. */
internal object MeshChatStore {
    private const val PREFS = "vanta_mesh_history"
    private const val DATA = "encrypted_history"
    private const val KEY_ALIAS = "vanta_mesh_history_key"
    private const val MAX_MESSAGES = 500

    @Synchronized fun add(context: Context, sender: String, content: String) {
        val messages = load(context).toMutableList()
        messages.add(StoredChatMessage(sender, content, System.currentTimeMillis()))
        save(context, messages.takeLast(MAX_MESSAGES))
    }

    @Synchronized fun load(context: Context): List<StoredChatMessage> {
        val encoded = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(DATA, null)
            ?: return emptyList()
        return try {
            val packed = Base64.decode(encoded, Base64.NO_WRAP)
            require(packed.size > 12)
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, getKey(), GCMParameterSpec(128, packed.copyOfRange(0, 12)))
            val json = JSONArray(String(cipher.doFinal(packed.copyOfRange(12, packed.size)), Charsets.UTF_8))
            (0 until json.length()).map { index ->
                val item = json.getJSONObject(index)
                StoredChatMessage(item.getString("sender"), item.getString("content"), item.getLong("timestamp"))
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    @Synchronized fun clear(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().remove(DATA).apply()
    }

    private fun save(context: Context, messages: List<StoredChatMessage>) {
        val array = JSONArray()
        messages.forEach { message ->
            array.put(JSONObject()
                .put("sender", message.sender)
                .put("content", message.content)
                .put("timestamp", message.timestamp))
        }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, getKey())
        val packed = cipher.iv + cipher.doFinal(array.toString().toByteArray(Charsets.UTF_8))
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString(DATA, Base64.encodeToString(packed, Base64.NO_WRAP))
            .apply()
    }

    private fun getKey(): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (store.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        generator.init(KeyGenParameterSpec.Builder(
            KEY_ALIAS,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
        ).setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setRandomizedEncryptionRequired(true)
            .build())
        return generator.generateKey()
    }
}

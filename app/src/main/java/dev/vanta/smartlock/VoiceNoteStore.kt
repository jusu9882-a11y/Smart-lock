package dev.vanta.smartlock

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Base64
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.Locale
import javax.crypto.Cipher
import javax.crypto.CipherInputStream
import javax.crypto.CipherOutputStream
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import java.security.KeyStore
import javax.crypto.spec.GCMParameterSpec

data class VoiceNoteFile(val file: File) {
    val encrypted: Boolean get() = file.extension == "vnta"
    val name: String get() = file.name.substringAfter('_').removeSuffix(".vnta")
    val sizeLabel: String get() = String.format(Locale.getDefault(), "%.1f MB", file.length() / 1048576.0)
}

object VoiceNoteStore {
    private const val DIRECTORY = "whatsapp_voice_notes"
    private const val MAX_BYTES = 50L * 1024L * 1024L
    private const val KEY_ALIAS = "vanta_voice_note_key"
    private const val IV_BYTES = 12

    fun list(context: Context): List<VoiceNoteFile> {
        val directory = File(context.filesDir, DIRECTORY)
        return (directory.listFiles()?.filter { it.isFile } ?: emptyList())
            .sortedByDescending { it.lastModified() }
            .map { VoiceNoteFile(it) }
    }

    fun importAudio(context: Context, uri: Uri): Result<File> = runCatching {
        val mime = context.contentResolver.getType(uri).orEmpty()
        require(mime.startsWith("audio/")) { "Please select an audio file." }

        val originalName = context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
            ?.use { cursor -> if (cursor.moveToFirst()) cursor.getString(0) else null } ?: "voice-note"
        val safeName = originalName.substringAfterLast('/').replace(Regex("[^A-Za-z0-9._-]"), "_")
            .take(90).ifBlank { "voice-note" }

        val directory = File(context.filesDir, DIRECTORY).apply { mkdirs() }
        val destination = File(directory, "${System.currentTimeMillis()}_$safeName.vnta")
        try {
            val input = context.contentResolver.openInputStream(uri)
                ?: error("Android could not open the selected file.")
            val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply {
                init(Cipher.ENCRYPT_MODE, getKey())
            }
            input.use { source ->
                CipherOutputStream(FileOutputStream(destination), cipher).use { output ->
                    val buffer = ByteArray(8192)
                    var total = 0L
                    while (true) {
                        val count = source.read(buffer)
                        if (count < 0) break
                        total += count
                        require(total <= MAX_BYTES) { "Audio file exceeds the 50 MB import limit." }
                        output.write(buffer, 0, count)
                    }
                }
            }
            require(destination.length() > IV_BYTES.toLong()) { "The selected audio file is empty." }
            destination
        } catch (error: Throwable) {
            destination.delete()
            throw error
        }
    }

    fun prepareForPlayback(context: Context, note: VoiceNoteFile): File {
        if (!note.encrypted) return note.file
        val playbackFile = File.createTempFile("vanta_play_", ".audio", context.cacheDir)
        try {
            FileInputStream(note.file).use { input ->
                val iv = ByteArray(IV_BYTES)
                var offset = 0
                while (offset < IV_BYTES) {
                    val count = input.read(iv, offset, IV_BYTES - offset)
                    if (count < 0) error("Encrypted audio file is incomplete.")
                    offset += count
                }
                val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply {
                    init(Cipher.DECRYPT_MODE, getKey(), GCMParameterSpec(128, iv))
                }
                CipherInputStream(input, cipher).use { decrypted ->
                    FileOutputStream(playbackFile).use { output -> decrypted.copyTo(output) }
                }
            }
            return playbackFile
        } catch (error: Throwable) {
            playbackFile.delete()
            throw error
        }
    }

    fun delete(note: VoiceNoteFile) {
        note.file.delete()
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

package dev.vanta.smartlock

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import java.io.File
import java.io.FileOutputStream
import java.util.Locale

data class VoiceNoteFile(val file: File) {
    val name: String get() = file.name.substringAfter('_', file.name)
    val sizeLabel: String get() = String.format(Locale.getDefault(), "%.1f MB", file.length() / 1048576.0)
}

object VoiceNoteStore {
    private const val DIRECTORY = "whatsapp_voice_notes"
    private const val MAX_BYTES = 50L * 1024L * 1024L

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
            ?.use { cursor ->
                if (cursor.moveToFirst()) cursor.getString(0) else null
            } ?: "voice-note"
        val safeName = originalName.substringAfterLast('/').replace(Regex("[^A-Za-z0-9._-]"), "_")
            .take(90).ifBlank { "voice-note" }

        val directory = File(context.filesDir, DIRECTORY).apply { mkdirs() }
        val destination = File(directory, "${System.currentTimeMillis()}_$safeName")
        val input = context.contentResolver.openInputStream(uri)
            ?: error("Android could not open the selected file.")
        input.use { source ->
            FileOutputStream(destination).use { output ->
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
        require(destination.length() > 0L) { "The selected audio file is empty." }
        destination
    }

    fun delete(note: VoiceNoteFile) {
        note.file.delete()
    }
}

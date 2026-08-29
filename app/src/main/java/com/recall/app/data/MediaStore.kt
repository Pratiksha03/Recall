package com.recall.app.data

import android.content.Context
import android.net.Uri
import java.io.File
import java.util.UUID

/**
 * Copies a picked file (image or audio) into the app's own private folder.
 *
 * When the user picks something from the gallery or a file browser, Android only
 * grants a temporary read permission on that URI — it can expire, and the user can
 * delete the original. So we copy the bytes in and remember our own path.
 * Uninstalling the app removes these files; nothing else on the phone can read them.
 */
object MediaStore {

    private const val IMAGE_DIR = "card_images"
    private const val AUDIO_DIR = "card_audio"

    fun copyImage(context: Context, source: Uri): String? =
        copyInto(context, source, IMAGE_DIR, "img", "jpg")

    fun copyAudio(context: Context, source: Uri): String? =
        copyInto(context, source, AUDIO_DIR, "aud", extensionOf(context, source) ?: "m4a")

    private fun copyInto(
        context: Context,
        source: Uri,
        dirName: String,
        prefix: String,
        extension: String
    ): String? = runCatching {
        val dir = File(context.filesDir, dirName).apply { mkdirs() }
        val target = File(dir, "${prefix}_${UUID.randomUUID()}.$extension")
        context.contentResolver.openInputStream(source)!!.use { input ->
            target.outputStream().use { output -> input.copyTo(output) }
        }
        target.absolutePath
    }.getOrNull()

    /** Best guess at a file extension from the MIME type, e.g. audio/mpeg -> mp3. */
    private fun extensionOf(context: Context, uri: Uri): String? =
        when (context.contentResolver.getType(uri)) {
            "audio/mpeg", "audio/mp3" -> "mp3"
            "audio/mp4", "audio/m4a", "audio/x-m4a" -> "m4a"
            "audio/ogg" -> "ogg"
            "audio/wav", "audio/x-wav" -> "wav"
            "audio/aac" -> "aac"
            "audio/flac" -> "flac"
            else -> null
        }

    fun delete(path: String) {
        runCatching { File(path).takeIf { it.exists() }?.delete() }
    }
}

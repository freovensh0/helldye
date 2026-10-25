package dev.scdeal.helldye.assistant

import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.os.Environment
import android.provider.DocumentsContract
import android.provider.MediaStore
import java.io.File

/**
 * Resolves a picked content URI to a real device path when the provider exposes one. The prompt then
 * carries `@path`, so the Pi host reads the file itself instead of receiving its bytes.
 */
object FilePaths {
    fun resolve(context: Context, uri: Uri): String? {
        if (uri.scheme == "file") return uri.path
        queryPath(context, uri)?.let { return it }
        if (uri.authority == "com.android.providers.media.documents") {
            val id = runCatching { DocumentsContract.getDocumentId(uri) }.getOrNull() ?: return null
            val parts = id.split(":", limit = 2)
            if (parts.size == 2) {
                val collection = when (parts[0]) {
                    "image" -> MediaStore.Images.Media.EXTERNAL_CONTENT_URI
                    "video" -> MediaStore.Video.Media.EXTERNAL_CONTENT_URI
                    "audio" -> MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
                    else -> null
                }
                val numericId = parts[1].toLongOrNull()
                if (collection != null && numericId != null) {
                    queryPath(context, ContentUris.withAppendedId(collection, numericId))?.let {
                        return it
                    }
                }
            }
        }
        if (uri.authority == "com.android.externalstorage.documents") {
            val id = runCatching { DocumentsContract.getDocumentId(uri) }.getOrNull() ?: return null
            val parts = id.split(":", limit = 2)
            if (parts.size == 2 && parts[0] == "primary") {
                return File(Environment.getExternalStorageDirectory(), parts[1]).absolutePath
            }
        }
        return null
    }

    fun appendReference(current: String, path: String): String {
        val reference = "@$path"
        return when {
            current.isBlank() -> "$reference "
            current.last().isWhitespace() -> "$current$reference "
            else -> "$current $reference "
        }
    }

    private fun queryPath(context: Context, uri: Uri): String? = runCatching {
        val projection = arrayOf(
            MediaStore.MediaColumns.DATA,
            MediaStore.MediaColumns.RELATIVE_PATH,
            MediaStore.MediaColumns.DISPLAY_NAME,
        )
        context.contentResolver.query(uri, projection, null, null, null)?.use { cursor ->
            if (!cursor.moveToFirst()) return@use null
            val dataIndex = cursor.getColumnIndex(MediaStore.MediaColumns.DATA)
            if (dataIndex >= 0 && !cursor.isNull(dataIndex)) {
                cursor.getString(dataIndex)?.takeIf { it.isNotBlank() }?.let { return@use it }
            }
            val nameIndex = cursor.getColumnIndex(MediaStore.MediaColumns.DISPLAY_NAME)
            val relativeIndex = cursor.getColumnIndex(MediaStore.MediaColumns.RELATIVE_PATH)
            val name = if (nameIndex >= 0 && !cursor.isNull(nameIndex)) {
                cursor.getString(nameIndex)
            } else {
                null
            }?.takeIf { it.isNotBlank() } ?: return@use null
            val relative = if (relativeIndex >= 0 && !cursor.isNull(relativeIndex)) {
                cursor.getString(relativeIndex)
            } else {
                null
            }?.trim('/')?.takeIf { it.isNotBlank() }
            val base = Environment.getExternalStorageDirectory()
            val path = if (relative != null) File(base, "$relative/$name") else File(base, name)
            path.absolutePath
        }
    }.getOrNull()
}

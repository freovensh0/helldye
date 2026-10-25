package dev.scdeal.helldye.assistant

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.util.Base64
import java.io.ByteArrayOutputStream

/** Encodes the one staged screenshot exactly once: opaque JPEG, no alpha, no EXIF, shared limits. */
object ScreenCapture {
    fun encode(bitmap: Bitmap): Attachment.Photo? {
        val size = ImageRules.scaledSize(bitmap.width, bitmap.height)
        val scaled = if (size.first != bitmap.width || size.second != bitmap.height) {
            Bitmap.createScaledBitmap(bitmap, size.first, size.second, true)
        } else {
            bitmap
        }
        return try {
            var current = scaled
            var owned = false
            var bytes = ByteArray(0)
            var attempts = 0
            while (true) {
                val opaque = Bitmap.createBitmap(
                    current.width,
                    current.height,
                    Bitmap.Config.ARGB_8888,
                )
                Canvas(opaque).apply {
                    drawColor(Color.BLACK)
                    drawBitmap(current, 0f, 0f, null)
                }
                val output = ByteArrayOutputStream()
                opaque.compress(Bitmap.CompressFormat.JPEG, ImageRules.JPEG_QUALITY, output)
                opaque.recycle()
                bytes = output.toByteArray()
                if (bytes.size <= ImageRules.MAX_BYTES_PER_IMAGE || attempts >= 4) break
                val next = Bitmap.createScaledBitmap(
                    current,
                    ((current.width * 3) / 4).coerceAtLeast(1),
                    ((current.height * 3) / 4).coerceAtLeast(1),
                    true,
                )
                if (owned) current.recycle()
                current = next
                owned = true
                attempts++
            }
            val width = current.width
            val height = current.height
            if (owned) current.recycle()
            if (!ImageRules.fitsCapture(width, height, bytes.size)) {
                null
            } else {
                Attachment.Photo(
                    name = "SCREEN",
                    mimeType = "image/jpeg",
                    base64 = Base64.encodeToString(bytes, Base64.NO_WRAP),
                )
            }
        } finally {
            if (scaled !== bitmap) scaled.recycle()
        }
    }
}

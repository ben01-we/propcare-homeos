package app.propcare.homeos.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.UUID

/** Photos live in app-private storage and are shrunk before they are kept or sent. */
object Images {
    fun photosDir(context: Context) = File(context.filesDir, "photos").apply { mkdirs() }

    /** A private file + content Uri for the camera app to write into. */
    fun newCaptureTarget(context: Context): Pair<File, Uri> {
        val file = File(photosDir(context), "capture-${UUID.randomUUID()}.jpg")
        return file to FileProvider.getUriForFile(context, "${context.packageName}.files", file)
    }

    suspend fun decodeScaled(context: Context, uri: Uri, maxSide: Int): Bitmap = withContext(Dispatchers.IO) {
        val src = ImageDecoder.createSource(context.contentResolver, uri)
        ImageDecoder.decodeBitmap(src) { decoder, info, _ ->
            decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
            val w = info.size.width
            val h = info.size.height
            val scale = minOf(1f, maxSide.toFloat() / maxOf(w, h))
            if (scale < 1f) decoder.setTargetSize((w * scale).toInt().coerceAtLeast(1), (h * scale).toInt().coerceAtLeast(1))
        }
    }

    fun scale(bitmap: Bitmap, maxSide: Int): Bitmap {
        val s = minOf(1f, maxSide.toFloat() / maxOf(bitmap.width, bitmap.height))
        return if (s >= 1f) bitmap else Bitmap.createScaledBitmap(bitmap, (bitmap.width * s).toInt(), (bitmap.height * s).toInt(), true)
    }

    fun jpeg(bitmap: Bitmap, quality: Int): ByteArray =
        ByteArrayOutputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, quality, it); it.toByteArray() }

    /** Saves a 1280px copy for the job record and returns its path. */
    suspend fun saveForJob(context: Context, bitmap: Bitmap): String = withContext(Dispatchers.IO) {
        val file = File(photosDir(context), "job-${UUID.randomUUID()}.jpg")
        file.writeBytes(jpeg(scale(bitmap, 1280), 82))
        file.absolutePath
    }

    fun loadThumb(path: String, maxSide: Int): Bitmap? = runCatching {
        val opts = android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds = true }
        android.graphics.BitmapFactory.decodeFile(path, opts)
        var sample = 1
        while (maxOf(opts.outWidth, opts.outHeight) / (sample * 2) >= maxSide) sample *= 2
        android.graphics.BitmapFactory.decodeFile(path, android.graphics.BitmapFactory.Options().apply { inSampleSize = sample })
    }.getOrNull()

    fun deleteCaptures(context: Context) {
        photosDir(context).listFiles { f -> f.name.startsWith("capture-") }?.forEach { it.delete() }
    }
}

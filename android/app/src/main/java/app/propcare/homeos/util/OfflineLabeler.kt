package app.propcare.homeos.util

import android.graphics.Bitmap
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.label.ImageLabeling
import com.google.mlkit.vision.label.defaults.ImageLabelerOptions
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

/**
 * On-device image labels (Google ML Kit, bundled model). Works with no internet and
 * nothing leaves the phone. Used as the offline fallback when the online check is unavailable.
 */
object OfflineLabeler {
    private val labeler by lazy { ImageLabeling.getClient(ImageLabelerOptions.Builder().setConfidenceThreshold(0.6f).build()) }

    suspend fun labels(bitmap: Bitmap): List<String> = suspendCancellableCoroutine { cont ->
        labeler.process(InputImage.fromBitmap(bitmap, 0))
            .addOnSuccessListener { list -> if (cont.isActive) cont.resume(list.sortedByDescending { it.confidence }.take(5).map { it.text }) }
            .addOnFailureListener { if (cont.isActive) cont.resume(emptyList()) }
    }
}

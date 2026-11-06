package app.propcare.homeos

import android.app.Application
import android.content.Intent
import android.provider.MediaStore
import androidx.activity.result.contract.ActivityResultContracts
import app.propcare.homeos.util.Images
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class DemoCameraTest {
    @Test fun cameraUsesPrivateFileProviderUriAndCaptureContract() {
        val app = RuntimeEnvironment.getApplication()
        val (file, uri) = Images.newCaptureTarget(app)
        assertEquals("content", uri.scheme)
        assertTrue(file.canonicalPath.startsWith(Images.photosDir(app).canonicalPath + java.io.File.separator))
        val contract = ActivityResultContracts.TakePicture()
        val intent = contract.createIntent(app, uri)
        assertEquals(MediaStore.ACTION_IMAGE_CAPTURE, intent.action)
        @Suppress("DEPRECATION")
        assertEquals(uri, intent.getParcelableExtra<android.net.Uri>(MediaStore.EXTRA_OUTPUT))
        assertFalse(contract.parseResult(android.app.Activity.RESULT_CANCELED, Intent()))
        app.contentResolver.openOutputStream(uri)!!.use { it.write(byteArrayOf(1, 2, 3)) }
        assertEquals(3L, file.length())
        file.delete()
    }
}

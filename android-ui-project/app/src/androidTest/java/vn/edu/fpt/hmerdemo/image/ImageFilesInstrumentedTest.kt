package vn.edu.fpt.hmerdemo.image

import android.content.Context
import android.net.Uri
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ImageFilesInstrumentedTest {
    private val context: Context
        get() = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun cameraAndCropDestinationsAreWritableFileProviderJpegs() {
        val cameraUri = ImageFiles.createCameraUri(context)
        val cropUri = ImageFiles.createCropUri(context)

        assertNotEquals(cameraUri, cropUri)
        try {
            assertWritableJpeg(cameraUri)
            assertWritableJpeg(cropUri)
        } finally {
            ImageFiles.deleteOwnedImage(context, cameraUri)
            ImageFiles.deleteOwnedImage(context, cropUri)
        }
    }

    @Test
    fun ownedImagesCanBeDeletedWithoutTouchingExternalUris() {
        val cameraUri = ImageFiles.createCameraUri(context)
        assertWritableJpeg(cameraUri)

        assertTrue(ImageFiles.deleteOwnedImage(context, cameraUri))
        assertFalse(ImageFiles.deleteOwnedImage(context, Uri.parse("content://external/images/1")))
        assertUnreadable(cameraUri)
    }

    @Test
    fun transientCameraAndCropImagesCanBePurged() {
        val cameraUri = ImageFiles.createCameraUri(context)
        val cropUri = ImageFiles.createCropUri(context)
        assertWritableJpeg(cameraUri)
        assertWritableJpeg(cropUri)

        ImageFiles.clearTransientImages(context)

        assertUnreadable(cameraUri)
        assertUnreadable(cropUri)
    }

    private fun assertWritableJpeg(uri: Uri) {
        assertEquals("content", uri.scheme)
        assertEquals("${context.packageName}.fileprovider", uri.authority)
        assertEquals("image/jpeg", context.contentResolver.getType(uri))

        val stream = context.contentResolver.openOutputStream(uri, "w")
        assertNotNull(stream)
        stream!!.use { it.write(byteArrayOf(1, 2, 3)) }
    }

    private fun assertUnreadable(uri: Uri) {
        assertFalse(
            runCatching {
                context.contentResolver.openInputStream(uri)?.use { it.read() }
            }.isSuccess,
        )
    }
}

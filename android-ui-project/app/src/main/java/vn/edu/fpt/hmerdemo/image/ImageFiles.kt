package vn.edu.fpt.hmerdemo.image

import android.content.Context
import android.net.Uri
import androidx.core.content.FileProvider
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import vn.edu.fpt.hmerdemo.R
import vn.edu.fpt.hmerdemo.network.HmerApiException


object ImageFiles {
    private const val CAMERA_DIRECTORY = "camera"
    private const val CROP_DIRECTORY = "crops"

    private val sampleImages = listOf(
        R.drawable.sample_hard_01,
        R.drawable.sample_hard_02,
        R.drawable.sample_hard_03,
        R.drawable.sample_hard_04,
        R.drawable.sample_hard_05,
    )

    fun randomSampleUri(context: Context): Uri {
        val resourceId = sampleImages.random()
        val sampleDirectory = File(context.cacheDir, "samples").apply { mkdirs() }
        val sampleFile = File(sampleDirectory, "sample_${resourceId}.png")
        context.resources.openRawResource(resourceId).use { input ->
            sampleFile.outputStream().use { output -> input.copyTo(output) }
        }
        return uriForFile(context, sampleFile)
    }

    suspend fun copyImageToAppCache(
        context: Context,
        sourceUri: Uri,
    ): Uri = withContext(Dispatchers.IO) {
        val extension = when (context.contentResolver.getType(sourceUri)) {
            "image/png" -> "png"
            "image/webp" -> "webp"
            else -> "jpg"
        }
        val directory = File(context.cacheDir, CAMERA_DIRECTORY).apply { mkdirs() }
        val destination = File(
            directory,
            "import_${System.currentTimeMillis()}.$extension",
        )

        context.contentResolver.openInputStream(sourceUri)?.use { input ->
            destination.outputStream().use { output -> input.copyTo(output) }
        } ?: throw IllegalStateException("Không thể đọc ảnh đã chọn.")

        uriForFile(context, destination)
    }

    fun createCameraUri(context: Context): Uri {
        val directory = File(context.cacheDir, CAMERA_DIRECTORY).apply { mkdirs() }
        return uriForFile(
            context,
            File(directory, "capture_${System.currentTimeMillis()}.jpg"),
        )
    }

    fun createCropUri(context: Context): Uri {
        val directory = File(context.cacheDir, CROP_DIRECTORY).apply { mkdirs() }
        return uriForFile(
            context,
            File(directory, "crop_${System.currentTimeMillis()}.jpg"),
        )
    }

    fun deleteOwnedImage(context: Context, uri: Uri): Boolean {
        if (uri.authority != "${context.packageName}.fileprovider") return false

        return runCatching {
            context.contentResolver.delete(uri, null, null) > 0
        }.getOrDefault(false)
    }

    fun clearTransientImages(context: Context) {
        listOf(CAMERA_DIRECTORY, CROP_DIRECTORY).forEach { directoryName ->
            File(context.cacheDir, directoryName)
                .listFiles()
                ?.filter(File::isFile)
                ?.forEach(File::delete)
        }
    }

    suspend fun readImageBytes(context: Context, uri: Uri): ByteArray =
        withContext(Dispatchers.IO) {
            context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                ?: throw HmerApiException(
                    "IMAGE_DECODE_FAILED",
                    "Không thể đọc ảnh đã cắt.",
                )
        }

    private fun uriForFile(context: Context, file: File): Uri =
        FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file,
        )
}

package vn.edu.fpt.hmerdemo.image

import android.content.Context
import android.graphics.BitmapFactory
import android.net.Uri
import android.provider.OpenableColumns
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import vn.edu.fpt.hmerdemo.ui.HmerErrorCode
import vn.edu.fpt.hmerdemo.ui.UiError
import vn.edu.fpt.hmerdemo.ui.toUiError


object ImageValidator {
    suspend fun validate(context: Context, uri: Uri): UiError? =
        withContext(Dispatchers.IO) {
            val supportedTypes = setOf("image/jpeg", "image/png", "image/webp")
            val mimeType = context.contentResolver.getType(uri)
            if (mimeType != null && mimeType !in supportedTypes) {
                return@withContext HmerErrorCode.UNSUPPORTED_IMAGE_TYPE.toUiError()
            }

            val size = runCatching {
                context.contentResolver.query(
                    uri,
                    arrayOf(OpenableColumns.SIZE),
                    null,
                    null,
                    null,
                )?.use { cursor ->
                    if (cursor.moveToFirst()) cursor.getLong(0) else -1L
                } ?: -1L
            }.getOrDefault(-1L)
            if (size > 10L * 1024L * 1024L) {
                return@withContext HmerErrorCode.IMAGE_TOO_LARGE.toUiError()
            }

            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            val decoded = runCatching {
                context.contentResolver.openInputStream(uri)?.use { stream ->
                    BitmapFactory.decodeStream(stream, null, bounds)
                }
            }
            if (decoded.isFailure || bounds.outWidth <= 0 || bounds.outHeight <= 0) {
                return@withContext HmerErrorCode.IMAGE_DECODE_FAILED.toUiError()
            }
            if (bounds.outWidth < 64 || bounds.outHeight < 64) {
                return@withContext HmerErrorCode.IMAGE_TOO_SMALL.toUiError()
            }
            null
        }
}

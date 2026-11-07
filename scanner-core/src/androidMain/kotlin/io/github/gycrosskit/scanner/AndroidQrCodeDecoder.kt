package io.github.gycrosskit.scanner

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import java.io.ByteArrayInputStream
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import zxingcpp.BarcodeReader

/** Android 相册二维码解码器；在 Default dispatcher 解码，长边采样至最多 2048 px。 */
object AndroidQrCodeDecoder : QrCodeDecoder {
    override suspend fun decode(bytes: ByteArray): String? = withContext(Dispatchers.Default) {
        coroutineContext.ensureActive()
        val result = runCatching {
            val bitmap = prepareQrBitmap(bytes) ?: return@runCatching null
            try {
                val options = BarcodeReader.Options().apply {
                    formats = setOf(BarcodeReader.Format.QR_CODE)
                    tryHarder = true
                    tryRotate = true
                    tryInvert = true
                    tryDownscale = true
                    tryDenoise = true
                }
                BarcodeReader(options).read(bitmap).firstOrNull()?.text
            } finally {
                bitmap.recycle()
            }
        }.getOrElse { error ->
            if (error is CancellationException) throw error
            null
        }
        coroutineContext.ensureActive()
        result
    }
}

/** 返回的 bitmap 由调用方释放。 */
internal fun prepareQrBitmap(bytes: ByteArray): Bitmap? {
    if (bytes.isEmpty() || bytes.size > MAX_QR_IMAGE_BYTES) return null
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
    if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
    var sample = 1
    while (maxOf(bounds.outWidth, bounds.outHeight) / sample > QR_BITMAP_MAX_DIMENSION) sample *= 2
    val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply {
        inSampleSize = sample
        inPreferredConfig = Bitmap.Config.ARGB_8888
    }) ?: return null
    try {
        val orientation = try {
            ExifInterface(ByteArrayInputStream(bytes)).getAttributeInt(
                ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL,
            )
        } catch (error: Exception) {
            if (error is CancellationException) throw error
            ExifInterface.ORIENTATION_NORMAL
        }
        val matrix = Matrix().apply {
            when (orientation) {
                ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> setScale(-1f, 1f)
                ExifInterface.ORIENTATION_ROTATE_180 -> setRotate(180f)
                ExifInterface.ORIENTATION_FLIP_VERTICAL -> setScale(1f, -1f)
                ExifInterface.ORIENTATION_TRANSPOSE -> { setRotate(90f); postScale(-1f, 1f) }
                ExifInterface.ORIENTATION_ROTATE_90 -> setRotate(90f)
                ExifInterface.ORIENTATION_TRANSVERSE -> { setRotate(-90f); postScale(-1f, 1f) }
                ExifInterface.ORIENTATION_ROTATE_270 -> setRotate(-90f)
            }
        }
        val normalized = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, false)
        if (normalized !== bitmap) bitmap.recycle()
        return normalized
    } catch (error: Throwable) {
        bitmap.recycle()
        throw error
    }
}

private const val QR_BITMAP_MAX_DIMENSION = 2048

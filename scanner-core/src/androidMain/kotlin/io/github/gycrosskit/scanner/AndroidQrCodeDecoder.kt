package io.github.gycrosskit.scanner

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import com.google.zxing.BinaryBitmap
import com.google.zxing.PlanarYUVLuminanceSource
import com.google.zxing.common.HybridBinarizer
import com.google.zxing.qrcode.QRCodeReader
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.ensureActive

/** Android 相册二维码解码器；在 Default dispatcher 解码，长边采样至最多 2048 px，不持有 UI renderer。 */
object AndroidQrCodeDecoder : QrCodeDecoder {
    override suspend fun decode(bytes: ByteArray): String? = withContext(Dispatchers.Default) {
        coroutineContext.ensureActive()
        if (bytes.isEmpty() || bytes.size > MAX_QR_IMAGE_BYTES) return@withContext null
        val result = runCatching {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return@runCatching null
            var sample = 1
            while (maxOf(bounds.outWidth, bounds.outHeight) / sample > QR_BITMAP_MAX_DIMENSION) sample *= 2
            val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply {
                inSampleSize = sample
                inPreferredConfig = Bitmap.Config.RGB_565
            }) ?: return@runCatching null
            try {
                val luminance = bitmap.toLuminanceBytes()
                QRCodeReader().decode(
                    BinaryBitmap(
                        HybridBinarizer(
                            PlanarYUVLuminanceSource(
                                luminance,
                                bitmap.width,
                                bitmap.height,
                                0,
                                0,
                                bitmap.width,
                                bitmap.height,
                                false,
                            ),
                        ),
                    ),
                ).text
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

/** 逐行生成二维码灰度面，峰值只增加一份 ByteArray，不再创建整图 IntArray。 */
private fun Bitmap.toLuminanceBytes(): ByteArray {
    val luminance = ByteArray(width * height)
    val rowPixels = IntArray(width)
    repeat(height) { y ->
        getPixels(rowPixels, 0, width, 0, y, width, 1)
        val rowOffset = y * width
        repeat(width) { x ->
            val color = rowPixels[x]
            val red = color shr 16 and 0xFF
            val green = color shr 8 and 0xFF
            val blue = color and 0xFF
            luminance[rowOffset + x] = ((red + green * 2 + blue) / 4).toByte()
        }
    }
    return luminance
}

private const val QR_BITMAP_MAX_DIMENSION = 2048

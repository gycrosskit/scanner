package io.github.gycrosskit.scanner

import android.graphics.Bitmap
import android.graphics.Color
import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.ByteArrayOutputStream
import kotlin.test.assertEquals
import kotlin.test.assertNull

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class AndroidQrCodeDecoderTest {
    @Test fun decodesRealQrPixelsAndRejectsInvalidOrBlankImages() = runBlocking {
        assertNull(AndroidQrCodeDecoder.decode(byteArrayOf()))
        assertNull(AndroidQrCodeDecoder.decode(byteArrayOf(1, 2, 3)))
        val bitmap = Bitmap.createBitmap(256, 256, Bitmap.Config.ARGB_8888)
        try {
            bitmap.eraseColor(Color.WHITE)
            val blank = ByteArrayOutputStream()
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, blank)
            assertNull(AndroidQrCodeDecoder.decode(blank.toByteArray()))
            val expected = "https://example.com/qr?value=123"
            val matrix = QRCodeWriter().encode(expected, BarcodeFormat.QR_CODE, 256, 256)
            for (y in 0 until bitmap.height) {
                for (x in 0 until bitmap.width) {
                    bitmap.setPixel(x, y, if (matrix[x, y]) Color.BLACK else Color.WHITE)
                }
            }
            val encoded = ByteArrayOutputStream()
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, encoded)
            assertEquals(expected, AndroidQrCodeDecoder.decode(encoded.toByteArray()))
        } finally {
            bitmap.recycle()
        }
    }
}

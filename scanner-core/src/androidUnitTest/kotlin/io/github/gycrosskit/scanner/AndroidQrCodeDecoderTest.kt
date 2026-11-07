package io.github.gycrosskit.scanner

import android.graphics.Bitmap
import android.graphics.Color
import com.google.zxing.BarcodeFormat
import com.google.zxing.BinaryBitmap
import com.google.zxing.RGBLuminanceSource
import com.google.zxing.common.HybridBinarizer
import com.google.zxing.qrcode.QRCodeReader
import com.google.zxing.qrcode.QRCodeWriter
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.ByteArrayOutputStream
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class AndroidQrCodeDecoderTest {
    @Test fun rejectsInvalidAndOversizedImagesAndSamplesLargeImages() {
        assertNull(prepareQrBitmap(byteArrayOf()))
        assertNull(prepareQrBitmap(byteArrayOf(1, 2, 3)))
        val image = Bitmap.createBitmap(4100, 100, Bitmap.Config.ARGB_8888)
        try {
            val bytes = image.encode(Bitmap.CompressFormat.PNG)
            assertNull(prepareQrBitmap(bytes.copyOf(MAX_QR_IMAGE_BYTES + 1)))
            val sampled = assertNotNull(prepareQrBitmap(bytes))
            try {
                assertTrue(maxOf(sampled.width, sampled.height) <= 2048)
                assertEquals(Bitmap.Config.ARGB_8888, sampled.config)
            } finally { sampled.recycle() }
        } finally { image.recycle() }
    }

    @Test fun normalizesAllExifOrientations() {
        val colors = listOf(Color.RED, Color.GREEN, Color.BLUE, Color.CYAN, Color.MAGENTA, Color.YELLOW)
        val image = Bitmap.createBitmap(120, 80, Bitmap.Config.ARGB_8888)
        try {
            for (y in 0 until image.height) for (x in 0 until image.width) {
                image.setPixel(x, y, colors[(y / 40) * 3 + x / 40])
            }
            val jpeg = image.encode(Bitmap.CompressFormat.JPEG)
            val expected = listOf("ABCDEF", "CBAFED", "FEDCBA", "DEFABC", "ADBECF", "DAEBFC", "FCEBDA", "CFBEAD")
            for (orientation in 1..8) {
                val normalized = assertNotNull(prepareQrBitmap(jpeg.withOrientation(orientation)))
                try {
                    val columns = if (orientation <= 4) 3 else 2
                    val actual = buildString {
                        for (y in 0 until normalized.height / 40) for (x in 0 until columns) {
                            val pixel = normalized.getPixel(x * 40 + 20, y * 40 + 20)
                            val nearest = colors.indices.minBy { i ->
                                val color = colors[i]
                                (Color.red(pixel) - Color.red(color)).let { it * it } +
                                    (Color.green(pixel) - Color.green(color)).let { it * it } +
                                    (Color.blue(pixel) - Color.blue(color)).let { it * it }
                            }
                            append('A' + nearest)
                        }
                    }
                    assertEquals(expected[orientation - 1], actual, "EXIF orientation $orientation")
                } finally { normalized.recycle() }
            }
            val noExif = assertNotNull(prepareQrBitmap(jpeg))
            noExif.recycle()
        } finally { image.recycle() }
    }

    @Test fun preservesQrPixelsForDecoding() {
        val expected = "https://example.com/qr?value=123"
        val matrix = QRCodeWriter().encode(expected, BarcodeFormat.QR_CODE, 256, 256)
        val image = Bitmap.createBitmap(256, 256, Bitmap.Config.ARGB_8888)
        try {
            for (y in 0 until image.height) for (x in 0 until image.width) {
                image.setPixel(x, y, if (matrix[x, y]) Color.BLACK else Color.WHITE)
            }
            val prepared = assertNotNull(prepareQrBitmap(image.encode(Bitmap.CompressFormat.PNG)))
            try {
                val pixels = IntArray(prepared.width * prepared.height)
                prepared.getPixels(pixels, 0, prepared.width, 0, 0, prepared.width, prepared.height)
                val source = RGBLuminanceSource(prepared.width, prepared.height, pixels)
                assertEquals(expected, QRCodeReader().decode(BinaryBitmap(HybridBinarizer(source))).text)
            } finally { prepared.recycle() }
        } finally { image.recycle() }
    }

    private fun Bitmap.encode(format: Bitmap.CompressFormat): ByteArray =
        ByteArrayOutputStream().also { compress(format, 100, it) }.toByteArray()

    private fun ByteArray.withOrientation(orientation: Int): ByteArray {
        // JPEG APP1: little-endian TIFF with a single orientation entry.
        val exif = byteArrayOf(
            0xff.toByte(), 0xe1.toByte(), 0, 34, 69, 120, 105, 102, 0, 0,
            73, 73, 42, 0, 8, 0, 0, 0, 1, 0, 0x12, 1, 3, 0, 1, 0, 0, 0,
            orientation.toByte(), 0, 0, 0, 0, 0, 0, 0,
        )
        return copyOfRange(0, 2) + exif + copyOfRange(2, size)
    }
}

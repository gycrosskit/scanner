package io.github.gycrosskit.scanner

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.coroutines.Continuation
import kotlin.coroutines.startCoroutine
import kotlin.test.assertTrue
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.Implementation
import org.robolectric.annotation.Implements

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], shadows = [CancellingBitmapFactory::class])
class AndroidQrCodeCancellationTest {
    @Test fun cancellationDuringDecodeCannotBecomeNullOnTheSameDispatcher() {
        for (throwCancellation in listOf(false, true)) {
            val owner = Job()
            CancellingBitmapFactory.onDecode = {
                if (throwCancellation) throw CancellationException("decoder cancellation")
                owner.cancel()
            }
            val finished = CountDownLatch(1)
            var captured: Result<String?>? = null
            suspend { AndroidQrCodeDecoder.decode(byteArrayOf(1)) }.startCoroutine(object : Continuation<String?> {
                // decode uses Default too: its return must not depend on a dispatcher switch.
                override val context = Dispatchers.Default + owner
                override fun resumeWith(result: Result<String?>) { captured = result; finished.countDown() }
            })
            assertTrue(finished.await(3, TimeUnit.SECONDS), "decoder did not finish")
            assertTrue(checkNotNull(captured).exceptionOrNull() is CancellationException)
        }
    }
}

@Implements(BitmapFactory::class)
class CancellingBitmapFactory {
    companion object {
        var onDecode: () -> Unit = {}
        @JvmStatic @Implementation
        fun decodeByteArray(bytes: ByteArray, offset: Int, length: Int, options: BitmapFactory.Options?): Bitmap? {
            onDecode()
            return null
        }
    }
}

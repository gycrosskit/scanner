package io.github.gycrosskit.scanner

import kotlin.coroutines.Continuation
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.coroutines.startCoroutine
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.Job
import kotlinx.coroutines.CancellationException

class IosQrCodeDecoderTest {
    @Test fun cancellationBeforeAndDuringSynchronousBridgeCannotDeliverText() {
        for (before in listOf(true, false)) {
            val owner = Job()
            if (before) owner.cancel()
            var calls = 0
            val decoder = IosQrCodeDecoder { calls++; owner.cancel(); "late" }
            var captured: Result<String?>? = null
            suspend { decoder.decode(byteArrayOf(1)) }.startCoroutine(object : Continuation<String?> {
                override val context = owner
                override fun resumeWith(result: Result<String?>) { captured = result }
            })
            assertTrue(checkNotNull(captured).exceptionOrNull() is CancellationException)
            assertEquals(if (before) 0 else 1, calls)
        }
    }

    @Test fun oversizedInputNeverCopiesIntoNativeBridge() {
        var calls = 0
        val decoder = IosQrCodeDecoder { calls++; "value" }
        var captured: Result<String?>? = null
        suspend { decoder.decode(ByteArray(MAX_QR_IMAGE_BYTES + 1)) }.startCoroutine(
            object : Continuation<String?> {
                override val context = EmptyCoroutineContext
                override fun resumeWith(result: Result<String?>) { captured = result }
            })
        assertNull(checkNotNull(captured).getOrThrow())
        assertEquals(0, calls)
    }
}

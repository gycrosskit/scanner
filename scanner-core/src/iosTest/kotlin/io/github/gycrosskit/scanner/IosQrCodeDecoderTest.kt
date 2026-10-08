package io.github.gycrosskit.scanner

import kotlin.coroutines.Continuation
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.coroutines.startCoroutine
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class IosQrCodeDecoderTest {
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

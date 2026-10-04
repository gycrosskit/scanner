package io.github.gycrosskit.scanner.kuikly
import com.tencent.kuikly.core.nvi.serialization.json.JSONObject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.test.*
import kotlin.test.*
@OptIn(ExperimentalCoroutinesApi::class)
class ScannerModuleLifecycleTest {
    @Test fun `dispose between scan callback and dispatch cancels delivery`() = runTest {
        val module = ScannerModule()
        val result = async { module.scanCode() }
        runCurrent()
        module.response(JSONObject().apply { put("status", "decoded"); put("value", "old") })
        module.dispose()
        runCurrent()
        assertFailsWith<CancellationException> { result.await() }
    }
    @Test fun `dispose between decode callback and dispatch cancels delivery`() = runTest {
        val module = ScannerModule()
        val result = async { module.decode(byteArrayOf(1)) }
        runCurrent()
        module.response(JSONObject().apply { put("status", "decoded"); put("value", "old") })
        module.dispose()
        runCurrent()
        assertFailsWith<CancellationException> { result.await() }
    }
    @Test fun `live scan preserves result and user cancellation`() = runTest {
        val module = ScannerModule()
        val first = async { module.scanCode() }; runCurrent()
        module.response(JSONObject().apply { put("status", "decoded"); put("value", "qr") })
        assertEquals("qr", first.await())
        val second = async { module.scanCode() }; runCurrent()
        module.response(JSONObject().apply { put("status", "cancelled") })
        assertNull(second.await())
    }
}

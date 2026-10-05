package io.github.gycrosskit.scanner.kuikly
import com.tencent.kuikly.core.nvi.serialization.json.JSONObject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.test.*
import kotlin.test.*
@OptIn(ExperimentalCoroutinesApi::class)
class ScannerModuleLifecycleTest {
    @Test fun `empty content skips bridge and cancelled decode cannot affect next request`() = runTest {
        val module = ScannerModule()
        assertNull(module.decode(byteArrayOf()))
        assertTrue(module.calls.isEmpty())
        val old = async { module.decode(byteArrayOf(1)) }
        runCurrent()
        val oldResponse = module.response
        old.cancel()
        runCurrent()
        assertEquals(1, module.removedCallbacks)
        val current = async { module.decode(byteArrayOf(2)) }
        runCurrent()
        oldResponse(JSONObject().apply { put("status", "decoded"); put("value", "late") })
        runCurrent()
        assertFalse(current.isCompleted)
        module.response(JSONObject().apply { put("status", "not_found") })
        assertNull(current.await())
        module.dispose()
    }

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

    @Test fun `background cancellation cleans callback on page dispatcher after dispose`() = runTest {
        val ownerThread = Thread.currentThread()
        val module = ScannerModule()
        val result = async { module.decode(byteArrayOf(1)) }
        runCurrent()
        Thread { result.cancel() }.apply { start(); join() }
        assertEquals(0, module.removedCallbacks)
        module.dispose()
        runCurrent()
        assertFailsWith<CancellationException> { result.await() }
        assertEquals(listOf(ownerThread), module.callbackRemovalThreads)
        module.dispose()
        runCurrent()
        assertEquals(1, module.removedCallbacks)
    }

    @Test fun `native response after background cancellation cannot deliver or duplicate cleanup`() = runTest {
        val ownerThread = Thread.currentThread()
        val module = ScannerModule()
        val result = async { module.scanCode() }
        runCurrent()
        Thread { result.cancel() }.apply { start(); join() }
        assertEquals(0, module.removedCallbacks)
        module.response(JSONObject().apply { put("status", "decoded"); put("value", "old") })
        module.dispose()
        runCurrent()
        assertFailsWith<CancellationException> { result.await() }
        assertEquals(listOf(ownerThread), module.callbackRemovalThreads)
    }

    @Test fun `cancellation while callback registers removes it only once`() = runTest {
        val ownerThread = Thread.currentThread()
        val module = ScannerModule()
        lateinit var result: Deferred<String?>
        module.beforeReturn = { Thread { result.cancel() }.apply { start(); join() } }
        result = async { module.scanCode() }
        runCurrent()
        module.dispose()
        runCurrent()
        assertFailsWith<CancellationException> { result.await() }
        assertEquals(listOf(ownerThread), module.callbackRemovalThreads)
    }
}

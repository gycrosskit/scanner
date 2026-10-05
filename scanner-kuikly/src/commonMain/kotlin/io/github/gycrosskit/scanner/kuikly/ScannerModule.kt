package io.github.gycrosskit.scanner.kuikly

import io.github.gycrosskit.scanner.QrCodeDecoder
import com.tencent.kuikly.core.module.CallbackRef
import com.tencent.kuikly.core.module.Module
import com.tencent.kuikly.core.nvi.serialization.json.JSONObject
import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.ContinuationInterceptor
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.coroutines.coroutineContext
import kotlin.coroutines.resume
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

/** 每页一个实例；调用协程使用页面 dispatcher，dispose 在同一 Kuikly Context 执行。 */
@OptIn(ExperimentalEncodingApi::class)
class ScannerModule : Module(), QrCodeDecoder {
    private var disposed = false
    private val pending = mutableSetOf<CancellableContinuation<JSONObject?>>()
    override fun moduleName(): String = NAME
    override suspend fun decode(bytes: ByteArray): String? {
        if (bytes.isEmpty() || bytes.size > MAX_BYTES) return null
        val result = await("decode", JSONObject().apply { put("data", Base64.encode(bytes)) })
        return result?.optString("value")?.takeIf { result.optString("status") == "decoded" && it.isNotEmpty() }
    }

    /** 用户取消返回 null，设备或 SDK 失败交给页面错误出口，不能伪装成取消。 */
    suspend fun scanCode(): String? {
        val result = await("scan", JSONObject()) ?: error("扫码宿主不可用")
        return when (result.optString("status")) {
            "decoded" -> result.optString("value").takeIf { it.isNotBlank() }
                ?: error("系统未返回二维码")
            "cancelled" -> null
            else -> error("无法打开扫码，请稍后重试")
        }
    }

    private suspend fun await(method: String, args: JSONObject): JSONObject? {
        if (disposed) return null
        val dispatcher = coroutineContext[ContinuationInterceptor] as CoroutineDispatcher
        val response = suspendCancellableCoroutine<JSONObject?> { continuation ->
            pending.add(continuation)
            var callbackRef: CallbackRef? = null
            continuation.invokeOnCancellation {
                // 取消可来自后台；直接投页面 dispatcher，不依赖已取消的 Job。
                dispatcher.dispatch(EmptyCoroutineContext) {
                    pending.remove(continuation)
                    callbackRef?.let(::removeCallback)
                    callbackRef = null
                }
            }
            callbackRef = toNative(false, method, args.toString(), { response ->
                pending.remove(continuation)
                if (continuation.isActive) continuation.resume(response)
            }, false).callbackRef
            if (!continuation.isActive) {
                callbackRef?.let(::removeCallback)
                callbackRef = null
            }
        }
        // 原生回调已完成也可能尚未调度消费；页面销毁后不能再投递二维码。
        if (disposed) throw CancellationException("ScannerModule is disposed")
        return response
    }

    fun dispose() {
        disposed = true
        pending.toList().forEach { it.cancel() }
        pending.clear()
    }

    companion object {
        const val NAME = "GycScanner"
        // ponytail: JSON Base64 增加临时内存；单次图片限 32 MiB，大文件需求改用分块二进制桥。
        private const val MAX_BYTES = 32 * 1024 * 1024
    }
}

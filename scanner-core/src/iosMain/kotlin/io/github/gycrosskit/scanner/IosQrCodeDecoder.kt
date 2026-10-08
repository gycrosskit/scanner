@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)
package io.github.gycrosskit.scanner

import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
import platform.Foundation.NSData
import platform.Foundation.create
import kotlinx.coroutines.ensureActive
import kotlin.coroutines.coroutineContext

/** Swift 宿主以 GycScannerNative.QrCodeDecoder.decode(data:) 实现此桥。 */
fun interface IosQrCodeBridge {
    /** 同步读取图片，不保留调用方数据；无效图片/无码返回 null。@param data 完整图片字节。 */
    fun decode(data: NSData): String?
}
/** 将非空图片复制为 NSData 并调用 Swift 桥，不自动切换线程。@param bridge 宿主的 CoreImage 解码桥。 */
class IosQrCodeDecoder(private val bridge: IosQrCodeBridge) : QrCodeDecoder {
    override suspend fun decode(bytes: ByteArray): String? {
        coroutineContext.ensureActive()
        if (bytes.isEmpty() || bytes.size > MAX_QR_IMAGE_BYTES) return null
        val result = bytes.usePinned { bridge.decode(NSData.create(bytes = it.addressOf(0), length = bytes.size.toULong())) }
        coroutineContext.ensureActive()
        return result
    }
}

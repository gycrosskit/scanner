@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)
package io.github.gycrosskit.scanner

import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
import platform.Foundation.NSData
import platform.Foundation.create

/** Swift 宿主以 GycScannerNative.QrCodeDecoder.decode(data:) 实现此桥。 */
fun interface IosQrCodeBridge { fun decode(data: NSData): String? }
class IosQrCodeDecoder(private val bridge: IosQrCodeBridge) : QrCodeDecoder {
    override suspend fun decode(bytes: ByteArray): String? {
        if (bytes.isEmpty()) return null
        return bytes.usePinned { bridge.decode(NSData.create(bytes = it.addressOf(0), length = bytes.size.toULong())) }
    }
}

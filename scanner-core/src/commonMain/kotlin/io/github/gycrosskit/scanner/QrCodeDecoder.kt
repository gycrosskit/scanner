package io.github.gycrosskit.scanner

/** 只返回二维码原始文本；无二维码或无效图片返回 null，协程取消继续向上传播。 */
fun interface QrCodeDecoder {
    suspend fun decode(bytes: ByteArray): String?
}

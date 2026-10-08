package io.github.gycrosskit.scanner

/** 只返回二维码原始文本；无二维码或无效图片返回 null，协程取消继续向上传播。 */
fun interface QrCodeDecoder {
    /**
     * 解码完整图片字节，不校验二维码业务格式，也不改变调用方数组。
     * @param bytes PNG/JPEG 等平台可读取的图片数据；空数组或超过 32 MiB 返回 null。
     * @return 首个二维码原始文本；无二维码、无效图片或不支持的格式返回 null。
     */
    suspend fun decode(bytes: ByteArray): String?
}

internal const val MAX_QR_IMAGE_BYTES = 32 * 1024 * 1024

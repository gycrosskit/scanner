import io.github.gycrosskit.scanner.QrCodeDecoder
suspend fun probe(decoder: QrCodeDecoder, bytes: ByteArray) = decoder.decode(bytes)

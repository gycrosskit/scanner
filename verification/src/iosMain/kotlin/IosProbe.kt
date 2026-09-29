import io.github.gycrosskit.scanner.IosQrCodeDecoder
import io.github.gycrosskit.scanner.IosQrCodeBridge
fun iosDecoder() = IosQrCodeDecoder(IosQrCodeBridge { null })

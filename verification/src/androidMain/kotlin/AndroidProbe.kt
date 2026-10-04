import android.content.Context
import io.github.gycrosskit.scanner.ScannerPreviewView
import io.github.gycrosskit.scanner.AndroidQrCodeDecoder
fun preview(context: Context) = ScannerPreviewView(context, onFailure = {}) {}.apply {
    setFeedbackEnabled(true)
}
suspend fun decode(bytes: ByteArray) = AndroidQrCodeDecoder.decode(bytes)

import io.github.gycrosskit.scanner.kuikly.ScannerModule
import io.github.gycrosskit.scanner.kuikly.ScannerPreviewView
fun module() = ScannerModule()
fun preview() = ScannerPreviewView().apply {
    createAttr().apply { running(true); scanFrameSize(208f) }
    createEvent().apply { onResult {}; onFailure {} }
}

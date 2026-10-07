import androidx.compose.runtime.Composable
import com.tencent.kuikly.compose.ui.Modifier
import io.github.gycrosskit.scanner.kuikly.ScannerPreviewHost
import io.github.gycrosskit.scanner.kuikly.ScannerPreviewView

fun unifiedTypedView() = ScannerPreviewView()

@Composable fun unifiedPreview() {
    ScannerPreviewHost(running = false, onResult = {}, onFailure = {}, modifier = Modifier)
}

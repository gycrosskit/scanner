package io.github.gycrosskit.scanner

import android.content.Context
import android.widget.FrameLayout
import com.google.zxing.BarcodeFormat
import com.journeyapps.barcodescanner.BarcodeView
import com.journeyapps.barcodescanner.CameraPreview
import com.journeyapps.barcodescanner.DefaultDecoderFactory
import com.journeyapps.barcodescanner.Size

/** 主线程调用；宿主先申请 CAMERA 权限，在 onPause/离开组合时暂停，在销毁时 release。 */
class ScannerPreviewView(
    context: Context,
    private var onFailure: ((Exception) -> Unit)? = null,
    private var onResult: ((String) -> Unit)?,
) : FrameLayout(context) {
    private val camera = BarcodeView(context).apply {
        decoderFactory = DefaultDecoderFactory(listOf(BarcodeFormat.QR_CODE))
    }
    private var running = false
    private var released = false
    private var generation = 0
    init {
        addView(camera, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
        camera.addStateListener(object : CameraPreview.StateListener {
            override fun previewSized() = Unit
            override fun previewStarted() = Unit
            override fun previewStopped() = Unit
            override fun cameraClosed() = Unit
            override fun cameraError(error: Exception) {
                if (running && !released) {
                    setRunning(false)
                    onFailure?.invoke(error)
                }
            }
        })
    }

    fun setScanFrameSize(pixels: Int) {
        require(pixels > 0)
        camera.framingRectSize = Size(pixels, pixels)
    }

    /** false → true 开始一次识别；一次只投递一个结果，再次扫描须由宿主显式启动。 */
    fun setRunning(value: Boolean) {
        if (released || running == value) return
        running = value
        val current = ++generation
        if (value) {
            camera.decodeSingle { result ->
                if (!released && running && current == generation && !result.text.isNullOrEmpty()) {
                    setRunning(false)
                    onResult?.invoke(result.text)
                }
            }
            camera.resume()
        } else {
            camera.stopDecoding()
            camera.pause()
        }
    }

    fun release() {
        setRunning(false)
        released = true
        onResult = null
        onFailure = null
    }

    override fun onDetachedFromWindow() {
        setRunning(false)
        super.onDetachedFromWindow()
    }
}

package io.github.gycrosskit.scanner

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.widget.FrameLayout
import com.google.zxing.BarcodeFormat
import com.google.zxing.client.android.BeepManager
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
    private var feedback: BeepManager? = null
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

    /** 默认不开启反馈；true 保留 ZXing 音量与音色，振动必须由宿主显式启用。 */
    fun setFeedbackEnabled(enabled: Boolean, vibrateEnabled: Boolean = false) {
        if (released) return
        if (!enabled && !vibrateEnabled) {
            feedback = null
            return
        }
        val manager = feedback ?: BeepManager(checkNotNull(context.findActivity()) {
            "Scanner feedback requires an Activity context"
        })
        manager.isBeepEnabled = enabled
        manager.isVibrateEnabled = vibrateEnabled
        feedback = manager
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
                    // 声音/振动失败不丢弃已经识别的二维码；SDK 自行释放短音的 MediaPlayer。
                    try { feedback?.playBeepSoundAndVibrate() } catch (_: RuntimeException) {}
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
        feedback = null
        onResult = null
        onFailure = null
    }

    override fun onDetachedFromWindow() {
        setRunning(false)
        super.onDetachedFromWindow()
    }
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

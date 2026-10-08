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
import kotlin.math.roundToInt

/**
 * 主线程创建和操作；宿主先申请 CAMERA 权限，在 onPause/离开组合时暂停，在销毁时 release。
 * @param context 用于相机 View 的宿主 Context；开启声音或振动时必须可解析出 Activity。
 * @param onFailure 当前扫描期的相机失败回调，默认 null；暂停后迟到错误忽略。
 * @param onResult 当前扫描期的单次二维码原始文本回调，可为 null；release 时释放引用。
 */
class ScannerPreviewView(
    context: Context,
    private var onFailure: ((Exception) -> Unit)? = null,
    private var onResult: ((String) -> Unit)?,
) : FrameLayout(context) {
    private val camera = BarcodeView(context).apply {
        val defaultFramePixels = (260 * resources.displayMetrics.density).roundToInt().coerceAtLeast(1)
        framingRectSize = Size(defaultFramePixels, defaultFramePixels)
        decoderFactory = DefaultDecoderFactory(listOf(BarcodeFormat.QR_CODE))
    }
    private var running = false
    private var released = false
    private var generation = 0
    private var feedback: BeepManager? = null
    private var onFeedback: (() -> Unit)? = null
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

    /** 设置正方形识别区，不绘制取景框。@param pixels 边长，正整数 px。 */
    fun setScanFrameSize(pixels: Int) {
        require(pixels > 0)
        camera.framingRectSize = Size(pixels, pixels)
    }

    /**
     * 默认不开启反馈；保留 ZXing 音量与音色，反馈失败不丢弃已识别结果。
     * @param enabled 是否播放提示音。
     * @param vibrateEnabled 默认 false，振动须由宿主声明权限并显式启用。
     */
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

    /** 与 iOS/Kuikly Compose 同语义的宿主反馈；默认无反馈，异常不丢结果。使用时关闭原生声音扩展避免重复。 */
    fun setFeedbackHandler(handler: (() -> Unit)?) {
        if (!released) onFeedback = handler
    }

    /**
     * false → true 开始一次识别；一次只投递一个结果，结果后再次扫描须显式启动。
     * @param value false 停止解码并暂停相机；release 后忽略。
     */
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
                    runCatching { onFeedback?.invoke() }
                    onResult?.invoke(result.text)
                }
            }
            camera.resume()
        } else {
            camera.stopDecoding()
            camera.pause()
        }
    }

    /** 主线程幂等释放相机和宿主回调；此实例不可再次启动。 */
    fun release() {
        setRunning(false)
        released = true
        feedback = null
        onFeedback = null
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

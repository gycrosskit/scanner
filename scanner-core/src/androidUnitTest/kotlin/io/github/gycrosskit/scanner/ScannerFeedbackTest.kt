package io.github.gycrosskit.scanner

import android.app.Activity
import com.google.zxing.BarcodeFormat
import com.google.zxing.Result
import com.google.zxing.client.android.BeepManager
import com.journeyapps.barcodescanner.BarcodeCallback
import com.journeyapps.barcodescanner.BarcodeResult
import com.journeyapps.barcodescanner.BarcodeView
import com.journeyapps.barcodescanner.CameraPreview
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.Implementation
import org.robolectric.annotation.Implements
import org.robolectric.shadows.ShadowViewGroup
import kotlin.test.assertEquals

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], shadows = [BarcodeViewBoundary::class, CameraPreviewBoundary::class, BeepBoundary::class])
class ScannerFeedbackTest {
    @Before fun reset() {
        BarcodeViewBoundary.callbacks.clear()
        BeepBoundary.count = 0
        BeepBoundary.fail = false
    }

    @Test fun defaultKeepsExistingBehaviorAndOptInFeedbackFiresOncePerAcceptedResult() {
        val activity = Robolectric.buildActivity(Activity::class.java).setup().get()
        val values = mutableListOf<String>()
        val view = ScannerPreviewView(activity, onResult = values::add)
        var hostFeedback = 0
        view.setFeedbackHandler { hostFeedback++ }
        view.setRunning(true)
        val first = BarcodeViewBoundary.callbacks.last()
        first.barcodeResult(result("first"))
        first.barcodeResult(result("duplicate"))
        assertEquals(listOf("first"), values)
        assertEquals(1, hostFeedback)
        assertEquals(0, BeepBoundary.count, "existing constructors do not opt in")
        view.setFeedbackEnabled(true)
        view.setRunning(true)
        val second = BarcodeViewBoundary.callbacks.last()
        first.barcodeResult(result("old"))
        second.barcodeResult(result("second"))
        second.barcodeResult(result("duplicate"))
        assertEquals(listOf("first", "second"), values)
        assertEquals(2, hostFeedback)
        assertEquals(1, BeepBoundary.count)
        view.release()
        view.setFeedbackEnabled(true)
        view.setRunning(true)
        second.barcodeResult(result("after-release"))
        assertEquals(1, BeepBoundary.count)
        assertEquals(2, hostFeedback, "release clears the host feedback closure")
    }

    @Test fun pauseBlankFramesDisabledAndFeedbackFailurePreserveResultContract() {
        val activity = Robolectric.buildActivity(Activity::class.java).setup().get()
        val values = mutableListOf<String>()
        val view = ScannerPreviewView(activity, onResult = values::add)
        view.setFeedbackEnabled(true)
        view.setRunning(true)
        val paused = BarcodeViewBoundary.callbacks.last()
        view.setRunning(false)
        paused.barcodeResult(result("late"))
        assertEquals(0, BeepBoundary.count)
        view.setRunning(true)
        val current = BarcodeViewBoundary.callbacks.last()
        current.barcodeResult(result(""))
        assertEquals(0, BeepBoundary.count)
        BeepBoundary.fail = true
        view.setFeedbackHandler { error("Host feedback unavailable") }
        current.barcodeResult(result("valid"))
        assertEquals(listOf("valid"), values, "feedback failure cannot discard recognized QR")
        assertEquals(1, BeepBoundary.count)
        view.setFeedbackEnabled(false)
        view.setRunning(true)
        BarcodeViewBoundary.callbacks.last().barcodeResult(result("quiet"))
        assertEquals(listOf("valid", "quiet"), values)
        assertEquals(1, BeepBoundary.count)
        view.release()
    }

    private fun result(value: String) = BarcodeResult(Result(value, null, emptyArray(), BarcodeFormat.QR_CODE), null)
}

@Implements(BarcodeView::class)
class BarcodeViewBoundary : CameraPreviewBoundary() {
    @Implementation fun decodeSingle(callback: BarcodeCallback) { callbacks.add(callback) }
    @Implementation fun stopDecoding() {}
    companion object { val callbacks = mutableListOf<BarcodeCallback>() }
}

@Implements(CameraPreview::class)
open class CameraPreviewBoundary : ShadowViewGroup() {
    @Implementation fun resume() {}
    @Implementation fun pause() {}
}

@Implements(BeepManager::class)
class BeepBoundary {
    @Implementation fun playBeepSoundAndVibrate() {
        count++
        if (fail) throw IllegalStateException("audio unavailable")
    }
    companion object { var count = 0; var fail = false }
}

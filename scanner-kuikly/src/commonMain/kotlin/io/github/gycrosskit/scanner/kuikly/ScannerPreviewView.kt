package io.github.gycrosskit.scanner.kuikly

import com.tencent.kuikly.core.base.Attr
import com.tencent.kuikly.core.base.DeclarativeBaseView
import com.tencent.kuikly.core.base.ViewContainer
import com.tencent.kuikly.core.base.event.Event
import com.tencent.kuikly.core.nvi.serialization.json.JSONObject

/** OHOS 原生 ScanKit 预览；宿主提供尺寸、权限、取景框和业务 UI。 */
class ScannerPreviewView : DeclarativeBaseView<ScannerPreviewAttr, ScannerPreviewEvent>() {
    override fun viewName() = NAME
    override fun createAttr() = ScannerPreviewAttr()
    override fun createEvent() = ScannerPreviewEvent()

    companion object { const val NAME = "GycScannerPreviewView" }
}

class ScannerPreviewAttr : Attr() {
    fun running(value: Boolean) { setProp("running", value) }
    /** 单位 vp，与原生 XComponent 的布局保持一致。 */
    fun scanFrameSize(value: Float) {
        require(value.isFinite() && value > 0f)
        setProp("scanFrameSize", value)
    }
}

class ScannerPreviewEvent : Event() {
    fun onResult(handler: (String) -> Unit) {
        register("onResult") { payload ->
            previewString(payload, "value")?.takeIf(String::isNotBlank)?.let(handler)
        }
    }
    fun onFailure(handler: (String) -> Unit) {
        register("onFailure") { payload -> handler(previewString(payload, "message").orEmpty()) }
    }
}

fun ViewContainer<*, *>.ScannerPreview(init: ScannerPreviewView.() -> Unit) {
    addChild(ScannerPreviewView(), init)
}

/** 原生事件支持对象或 JSON 字符串；畸形 SDK 数据不能抛出到相机线程。 */
internal fun previewString(payload: Any?, key: String): String? = runCatching {
    val json = when (payload) {
        is JSONObject -> payload
        is String -> JSONObject(payload)
        else -> return@runCatching null
    }
    json.opt(key) as? String
}.getOrNull()

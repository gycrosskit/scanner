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

    companion object { /** OHOS 原生预览 View 注册名。 */ const val NAME = "GycScannerPreviewView" }
}

/** OHOS 原生预览属性；宿主在 Kuikly Context 设置。 */
class ScannerPreviewAttr : Attr() {
    /** true 开始一次扫描，false 停止；结果后需显式重启。@param value 是否运行相机。 */
    fun running(value: Boolean) { setProp("running", value) }
    /** 设置识别区，不绘制取景框。@param value 正且有限的边长，单位 vp。 */
    fun scanFrameSize(value: Float) {
        require(value.isFinite() && value > 0f)
        setProp("scanFrameSize", value)
    }
}

/** 原生事件绑定，随 Kuikly View 生命周期释放。 */
class ScannerPreviewEvent : Event() {
    /** 绑定单次原始文本事件，空白/畸形事件忽略。@param handler 页面 Context 的结果消费者。 */
    fun onResult(handler: (String) -> Unit) {
        register("onResult") { payload ->
            previewString(payload, "value")?.takeIf(String::isNotBlank)?.let(handler)
        }
    }
    /** 绑定设备失败事件。@param handler 接收原生诊断文本，宿主负责用户文案。 */
    fun onFailure(handler: (String) -> Unit) {
        register("onFailure") { payload -> handler(previewString(payload, "message").orEmpty()) }
    }
}

/** 在当前容器添加原生扫码预览。@param init 配置布局、扫描属性和事件。 */
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

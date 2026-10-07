package io.github.gycrosskit.scanner.kuikly

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import com.tencent.kuikly.compose.extension.MakeKuiklyComposeNode
import com.tencent.kuikly.compose.ui.Modifier
import com.tencent.kuikly.compose.ui.unit.Dp
import com.tencent.kuikly.compose.ui.unit.dp

/**
 * 三端 Kuikly Compose 嵌入入口，宿主独占 running 状态和业务 UI，不再复制 Attr/Event 协议。
 * 原生节点销毁负责释放相机；false 暂停、true 可重启。宿主仍需先授权并注册平台 View。
 * onFeedback 默认无反馈；在已接受的结果上最多一次，异常不影响 onResult。
 * Android 原生声音/振动可另行开启；若使用统一反馈，请保持原生扩展关闭以免重复。
 */
@Composable
fun ScannerPreviewHost(
    running: Boolean,
    onResult: (String) -> Unit,
    onFailure: (String) -> Unit,
    modifier: Modifier = Modifier,
    scanFrameSize: Dp = 260.dp,
    onFeedback: (() -> Unit)? = null,
) {
    val currentRunning by rememberUpdatedState(running)
    val currentResult by rememberUpdatedState(onResult)
    val currentFailure by rememberUpdatedState(onFailure)
    val currentFeedback by rememberUpdatedState(onFeedback)
    MakeKuiklyComposeNode(
        factory = ::ScannerPreviewView,
        modifier = modifier,
        viewInit = {
            getViewEvent().onResult { value ->
                if (currentRunning) {
                    runCatching { currentFeedback?.invoke() }
                    currentResult(value)
                }
            }
            getViewEvent().onFailure { reason -> if (currentRunning) currentFailure(reason) }
            getViewAttr().scanFrameSize(scanFrameSize.value)
            getViewAttr().running(running)
        },
        viewUpdate = { view ->
            view.getViewAttr().scanFrameSize(scanFrameSize.value)
            view.getViewAttr().running(running)
        },
    )
}

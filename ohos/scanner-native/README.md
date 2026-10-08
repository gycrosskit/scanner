# @gycrosskit/scanner-native

2026-10-08 当前源码与三端/五入口边界见[功能与平台差异](../../docs/功能与平台差异.md)；本包只承担上文所述原生能力，以下版本和渠道记录按各自日期阅读。

HarmonyOS ScanKit 系统二维码扫码和图片解码。当前 HAR target/compatible SDK 为 HarmonyOS API 22。

```sh
ohpm install @gycrosskit/scanner-native@0.1.1
```

```typescript
import { GycScanner } from '@gycrosskit/scanner-native';
const scanner = new GycScanner(context);
const result = await scanner.scan();
// 页面销毁：
scanner.dispose();
```

context 为 UIAbilityContext，图片解码使用 decode(ArrayBuffer)。需要设备提供 ScanKit；纯 OpenHarmony 不保证支持。同实例仅一个系统扫码请求，dispose 取消回调但不主动关闭系统页。图片上限 32 MiB，临时文件 finally 清理；返回 decoded/not_found/cancelled/permission_denied/busy/invalid_content/failed。Kuikly 可注册 GycScannerModule。

[完整接入指南](https://github.com/gycrosskit/scanner/blob/main/docs/接入指南.md) · [开发与验证](https://github.com/gycrosskit/scanner/blob/main/docs/开发与验证.md) · [版本](https://github.com/gycrosskit/scanner/releases) · [问题反馈](https://github.com/gycrosskit/scanner/issues)。

Apache-2.0，见 [LICENSE](LICENSE)。


已有 `GycScannerPreviewView`，当前未发布候选调整ROI与结果归属；该View（Kuikly Renderer 注册名相同），提供 Surface 预览、running、
scanFrameSize(vp)、onResult/onFailure；取景框和权限 UI 留宿主。旧相机成功释放后才转移唯一 owner。
Kotlin View 和生命周期示例见仓库 docs/接入指南.md，历史 0.1.1 发布物不包含新 API。

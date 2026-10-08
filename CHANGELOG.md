# 更新日志

## 0.1.9（发布候选，2026-10-08）

同步解码前后检查协程取消，保留非空空白二维码文本；Swift 0.1.8 / HAR 0.1.4 沿用。

## 0.1.8（2026-10-08）

- 统一32MiB解码输入上限和默认260识别框；修正OHOS ROI与反馈/owner；补平台边界及回归。
- 更新功能、测试覆盖与平台差异文档；设备业务验收范围保持明确。

## 未发布

- 鸿蒙自定义预览的 scanCodeRect 与 ViewControl 同单位，移除重复 px2vp；ROI 使用正面积相交，与 iOS 规则一致，缺失/非有限/零面积/反向矩形继续 rescan。
- 实际生产 ETS 回归与 API22 HAR 编译通过；真实相机边界、密度与方向仍待设备验收。

## 0.1.7 候选（未发布）

- iOS 预览在当前有效 ROI 配置完成后才允许交付二维码；尺寸变更、暂停、释放关闭入口，旧异步配置不重新打开入口。保留 AVFoundation 的正面积相交规则，不要求 metadata timestamp 有效。
- init 和 setScanFrameSize 均要求正且有限的尺寸，公开 API、静态图片解码、Android/OHOS 算法保持；Maven/SPM 同版候选，HAR 保持 0.1.3。
- 实际生产 Swift delegate 的 Simulator fixture 证明旧源码失败、新源码通过，不代表设备首帧或跨 restart capture 时间语义验收。详见 [ROI 候选](docs/0.1.7-iOS-ROI候选.md)。

## 0.1.5（待发布）

Kuikly decode/scanCode 的后台取消清理派回调用时的页面 dispatcher；注册期间取消也只注销一次 callback。补齐页面 Context 合同和后台取消交错检查。Maven 制品与远程消费待验；HAR 保持 `0.1.3`，Swift Package 保持 `0.1.1`。

## 0.1.4

修复 Kuikly scan/decode 回调已完成但协程尚未消费时页面销毁的迟交付，保留请求归属和 native cancel。新 POM 补齐 Apache-2.0 元数据。

| 渠道 | 本轮版本 | 状态 |
| --- | --- | --- |
| Maven core/Kuikly | 0.1.4 | JitPack 全文件/hash 与 Android/OHOS/三 iOS 编译、Simulator 链接通过 |
| HarmonyOS HAR | 0.1.3 | 原生源码未变，沿用旧 Release 已验产物 |
| Swift Package GycScannerNative | 0.1.1 | 原生源码未变，保持既有精确消费版本 |


历史版本与验证范围见 [Releases](https://github.com/gycrosskit/scanner/releases)；真实消费与 Registry 状态见本轮发布验收。

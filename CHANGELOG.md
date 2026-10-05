# 更新日志

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

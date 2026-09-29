# GY CrossKit Scanner

二维码图片解码、Android/iOS 原生相机预览和 HarmonyOS ScanKit 系统扫码。仅返回原始文本，业务校验、扫码框、提示、导航及图片选择由宿主负责。

## 目录与平台

| 目录 | 能力 |
| --- | --- |
| `scanner-core/` | KMP `QrCodeDecoder` 契约；Android ZXing 解码、`ScannerPreviewView`；iOS Swift 解码桥 |
| `scanner-kuikly/` | OHOS Kuikly Module，连接独立 HAR |
| `iosApp/` | Swift Package 的 CoreImage 解码、AVFoundation 预览 |
| `ohos/` | `@gycrosskit/scanner-native` HAR：原生 Promise API 与 Kuikly Module |

Android 最低 API 24、iOS 15；HAR 使用 HarmonyOS API 22 构建，ScanKit 需要华为 HarmonyOS 设备能力。纯 OpenHarmony 设备不保证提供 ScanKit，失败返回 `failed`。Android/iOS 是可嵌入预览，鸿蒙使用系统扫码页。

## Android / KMP

仓库添加 `maven("https://jitpack.io")`。版本 `0.1.1`：

```kotlin
implementation("com.github.gycrosskit.scanner:scanner-core:0.1.1")
// 鸿蒙 Kuikly 项目另外添加：
implementation("com.github.gycrosskit.scanner:scanner-kuikly:0.1.1")
```

Kuikly OHOS 需要 Kotlin `2.2.21-1.0.0`、Kuikly `2.28.0-2.0.21-ohos`，并配置腾讯 Maven 仓库以解析 OHOS 版 Kotlin/coroutines。

```kotlin
val text = AndroidQrCodeDecoder.decode(imageBytes) // suspend，图片解码在后台线程
val preview = ScannerPreviewView(activity, onFailure = { error -> /* 提示 */ }) { rawText -> /* 宿主处理 */ }
preview.setScanFrameSize(720) // 可选，像素
preview.setRunning(true) // 先获得 CAMERA 权限
// onPause 或 Compose DisposableEffect 离开时：
preview.setRunning(false)
// 页面永久销毁时：
preview.release()
```

`AndroidView` 可承载原生控件；在 `update` 中根据宿主生命周期和权限状态设置 running，在 `onRelease` 调用 release。每次启动仅投递一个结果，结果后可显式再次启动。库不申请权限，不修改状态栏，不播放提示音。Android Manifest 会合并 CAMERA 声明；相机设备错误通过 onFailure 返回。

## iOS

Swift Package URL：`https://github.com/gycrosskit/scanner`，版本 `0.1.1`，产品 `GycScannerNative`。Info.plist 配置 `NSCameraUsageDescription`，宿主先申请相机权限。

```swift
import GycScannerNative
let text = QrCodeDecoder.decode(data: imageData)
let preview = ScannerPreviewView(onResult: { text in /* 宿主处理 */ },
                                 onFailure: { reason in /* 显示失败 */ })
preview.setRunning(true)
// 页面离开/应用进入后台先暂停；永久销毁时释放：
preview.setRunning(false)
preview.releaseCamera()
```

所有预览操作在主线程；图片解码可由宿主移至后台队列。KMP 宿主实现导出的 `IosQrCodeBridge.decode(data:)`，委托给 `GycScannerNative.QrCodeDecoder.decode(data:)`，再构建 `IosQrCodeDecoder(bridge)`。桥与预览不依赖宿主的 Shared 模块名称。

## HarmonyOS / Kuikly

原生项目安装 `@gycrosskit/scanner-native@0.1.1`（ohpm 上架状态见 Release；审核未通过前使用 Release HAR 文件）。安装命令：

```sh
ohpm install @gycrosskit/scanner-native
```

直接使用：

```typescript
const scanner = new GycScanner(context);
const result = await scanner.scan();
const decoded = await scanner.decode(imageArrayBuffer);
scanner.dispose();
```

`status` 为 `decoded / not_found / cancelled / permission_denied / busy / invalid_content / failed`。同实例只允许一个系统扫码请求，图片解码会在 finally 删除临时文件。dispose 后返回 cancelled；ScanKit 不提供主动关闭系统扫码页的接口。

Kuikly Kotlin Pager 注册 `ScannerModule.NAME to ScannerModule()`；ArkTS render 注册 `GycScannerModule.MODULE_NAME` 对应 `GycScannerModule`。Kotlin `scanCode()` 取消返回 null，SDK 错误抛异常，`decode(bytes)` 未识别返回 null。页面销毁时必须显式调用 Kotlin ScannerModule.dispose()，取消挂起请求并移除回调。仅允许 32 MiB 内的图片，Base64 桥会额外占用内存。

## Maven 发布

在 macOS 构建 Android/iOS/OHOS 完整 Maven 目录，打包为对应不可变标签的 `scanner-maven.tar.gz` Release 附件。JitPack 只下载并校验固定 SHA-256 后安装，避免构建宿主差异导致 iOS/OHOS 产物遗漏。`jitpack-install.sh` 中的 SHA 必须与标签附件一致；禁止覆盖已发布标签或归档。

```sh
bash gradlew publishToMavenLocal -Dmaven.repo.local=/tmp/scanner-release-maven
# 从已发布 Maven 目录验证独立消费者：
bash gradlew -p verification -PscannerMavenRepo=/tmp/scanner-release-maven compileDebugKotlinAndroid compileKotlinIosSimulatorArm64 compileKotlinOhosArm64
# 远程验证时去掉 scannerMavenRepo 参数。
```

## 验证与边界

```sh
ANDROID_HOME="$ANDROID_SDK_ROOT" bash gradlew :scanner-core:compileDebugKotlinAndroid :scanner-core:compileKotlinIosSimulatorArm64 :scanner-core:compileKotlinOhosArm64 :scanner-kuikly:compileKotlinOhosArm64
xcodebuild -scheme GycScannerNative -destination 'generic/platform=iOS Simulator' CODE_SIGNING_ALLOWED=NO build
node tests/scanner.cjs
# 在 DevEco 环境中：
cd ohos && ohpm install && hvigorw assembleHar --no-daemon
```

已验证范围以 Release 记录为准。Node 检查实际 ArkTS 逻辑的并发、取消、权限错误及临时文件清理；它不替代设备相机、权限拒绝、前后台切换、真实相册二维码和系统扫码界面验收。此版本不提供一维条码、多码选择、连续扫描或图片选择器。

代码依据 GY CrossKit 接入项目 `codex/harmony-production-integration` 中通用实现抽离。Android 使用 ZXing（Apache-2.0）；库使用 Apache-2.0。

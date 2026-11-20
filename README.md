# GY CrossKit Scanner

新版 Maven 发布由 Jenkins 生成同版本归档、`source-receipt.json` 和 `SHA256SUMS`。JitPack 核对标签来源、归档尺寸和摘要后安装同一份制品。历史发布按原版本 Tag 复验。

Kuikly 由宿主统一选择版本；组件构建基线、允许范围及消费验证边界见[功能与平台差异](docs/功能与平台差异.md#kuikly-版本与宿主选择)。 各渠道状态以精确发布记录为准，旧版本的接入与验收记录以对应 tag 为准。

统一版本候选为 `1.0.3`：Maven 与 Swift Package 待补发，私有 OHPM 沿用已发布的 `1.0.3`。本次只统一版本与消费默认值，保留 Maven `1.0.2` 的 Android ZXing-C++ 解码和既有 Swift/HAR 实现；旧标签、归档与摘要不变。候选需分别完成构建及精确公开消费后再使用，不能代算设备验收。当前功能合同见[功能与平台差异](docs/功能与平台差异.md)。

## 当前功能与平台边界

core 提供图片 QR 解码及 Android 原生预览，Swift 提供 CoreImage/AVFoundation；scanner-kuikly 确有三端 ScannerPreviewHost UI入口，系统扫码页仅 OHOS。

下列 Maven / Swift Package `1.0.3` 安装示例是未发布候选；HAR `1.0.3` 已发布。当前仍可使用 Maven `1.0.2` / Swift Package `1.0.1`。各渠道状态以精确 Release/Registry 收据为准。

当前测试覆盖、执行时点和未验收项集中见[验证范围](docs/功能与平台差异.md#验证范围)，复现命令见[开发与验证](docs/开发与验证.md)。

当前鸿蒙预览 ROI 的结果坐标沿 ViewControl 输入单位，不重复 px2vp；与 iOS 一致接受正面积相交，缺少位置或无效矩形继续扫描。配套 HAR `0.1.4` 包含此能力。

Kuikly Compose `ScannerPreviewHost`（Android/iOS/OHOS）提供共用反馈入口；当前包含 32 MiB 输入边界和默认 260 识别框。相机和视觉一致性仍须宿主及设备验收；历史发布与 ROI 候选记录见下方导航。

[历史完整源码审查](docs/完整源码审查.md) 列出全部生产文件、公开调用链、实际验证与未测项。

二维码图片解码、Android/iOS 原生相机预览和 HarmonyOS ScanKit 系统扫码。返回原始文本；业务格式校验、扫码框、提示、导航和图片选择由宿主负责。

## 平台与要求

| 平台 | 接入方式 | 系统要求 |
| --- | --- | --- |
| Android | KMP `scanner-core`，ZXing-C++ 图片解码、ZXing 原生预览 | API 24+ |
| iOS | KMP 解码 bridge，或 Swift Package `GycScannerNative` | iOS 15+，Swift tools 5.9 |
| HarmonyOS | 原生 HAR，或 `scanner-kuikly` + HAR | 当前 HAR 的 target/compatible SDK 均为 API 22；需要设备提供 ScanKit |

Android/iOS/HarmonyOS 提供嵌入式预览；HarmonyOS 另提供系统扫码页。纯 OpenHarmony 设备不保证有 ScanKit，失败返回 `failed`。KMP 使用 Kotlin `2.2.21-1.0.0`，Kuikly 使用 `2.29.1-2.0.21-ohos`；OHOS 工具链配置见接入指南。

## 架构与调用流程

图片解码走 `QrCodeDecoder`。Android 使用 ZXing-C++，iOS 通过宿主 Swift bridge 使用 CoreImage。HarmonyOS 的 `ScannerModule` 调用 ScanKit，系统扫码取消返回 `null`，失败按异常上报。页面销毁结束本地等待，系统扫码页由平台管理。嵌入式 `ScannerPreviewView` 单独管理相机生命周期。接线见[接入指南](docs/接入指南.md)，源码入口见 [AOCI 索引](aoci.txt)。

## 安装

```kotlin
// settings.gradle.kts
dependencyResolutionManagement {
    repositories {
        exclusiveContent {
            forRepository {
                maven("https://mirrors.tencent.com/nexus/repository/maven-tencent/")
            }
            filter { includeGroup("com.tencent.kuikly-open") }
        }
        maven("https://jitpack.io")
        maven("https://maven.eazytec-cloud.com/nexus/repository/maven-public/")
        google()
        mavenCentral()
    }
}
```

```kotlin
commonMain.dependencies {
    implementation("com.github.gycrosskit.scanner:scanner-core:1.0.3")
}
ohosArm64Main.dependencies {
    implementation("com.github.gycrosskit.scanner:scanner-kuikly:1.0.3")
}
```

iOS 在 Xcode 的 Package Dependencies 添加 `https://github.com/gycrosskit/scanner.git`，候选发布后选择精确版本 `1.0.3`（发布前沿用 `1.0.1`），产品 `GycScannerNative`。

HarmonyOS 原生包独立安装：

```sh
ohpm install @gycrosskit/scanner-native@1.0.3
```

2026-10-07 官方公共 Registry metadata 已包含精确版本 `0.1.3`（当时预览 UI 所需 HAR）；全新 cache 的精确 Registry 下载/安装仍在独立验证中。历史验收中 NOTFOUND 是对应时间的记录。

## 最小使用

```kotlin
import io.github.gycrosskit.scanner.*

// 协程内解码图片；未识别或无效图片返回 null，取消继续传播。
val text = AndroidQrCodeDecoder.decode(imageBytes)
// 主线程创建；先获得 CAMERA 权限。
val preview = ScannerPreviewView(activity, onFailure = { error ->
    // 宿主展示失败。
}) { rawText ->
    // 宿主校验并使用原始文本。
}
preview.setRunning(true)
// 页面离开或应用进入后台：
preview.setRunning(false)
// 页面永久销毁：
preview.release()
```

iOS 使用 `QrCodeDecoder.decode(data:)` 和 `ScannerPreviewView`；HarmonyOS 使用 `GycScanner(context).scan()` / `decode(bytes)`。平台完整示例见接入指南。

## 权限与边界

宿主先申请相机权限；Android CAMERA 声明由 AAR 合并，iOS Info.plist 需填写 `NSCameraUsageDescription`。预览主线程操作，一次启动只返回一个结果，继续扫描由宿主显式启动。Android/iOS 退出页面或后台时暂停，销毁时释放相机。

HarmonyOS 同实例只允许一个系统扫码请求；销毁时 `dispose()` 取消挂起回调。ScanKit 无主动关闭系统扫码页的接口；图片解码临时文件在 finally 清理，桥接图片上限 32 MiB，Base64 会额外占用内存。

不提供一维条码、多码选择、连续扫描、独立图片选择器或状态栏修改。编译与契约检查不代替真实设备相机、权限拒绝、前后台切换和二维码识别验收。

## 文档与帮助

- [接入指南](docs/接入指南.md)：平台初始化、权限声明和生命周期。
- [开发与验证](docs/开发与验证.md)：源码构建、检查命令与验收范围。
- [M13 候选验收](verification/嵌入式扫码候选验收.md)：本地测试、完整归档与未验收边界。
- [版本与发行说明](https://github.com/gycrosskit/scanner/releases)、[问题反馈](https://github.com/gycrosskit/scanner/issues)。

Apache-2.0，见 [LICENSE](LICENSE)。

## 历史发布记录

以下记录保留对应版本、渠道与验收时点，不替代顶部当前功能和安装基线。

- [0.1.7 iOS ROI 候选记录](docs/0.1.7-iOS-ROI候选.md)、[0.1.6 跨端行为与后续发布记录](docs/跨端行为候选.md)。
- <a id="015-prerelease"></a>[0.1.5 远程发布验收](docs/0.1.5远程发布验收.md)。
- <a id="014-prerelease"></a>[0.1.4 发布验收](docs/发布验收-0.1.4.md)。
- <a id="013-prerelease"></a>[0.1.2 安装失败与 0.1.3 嵌入式预览/API/远程验收](verification/嵌入式扫码候选验收.md)。

## 自动回归

候选归档入口：`VERSION=1.0.3 bash scripts/package-maven.sh`。仅在受控 macOS 构建环境执行；输出 `build/release/1.0.3/` 的 Maven 归档、`SHA256SUMS` 和 `source-receipt.json`。严格检查 12 个 publications 后冻结，新 checksum 必须取真实归档摘要。旧 `0.1.9` 验收与 checksum 保留原时点。

源码回归使用 `component-ci/verify`，见[统一远程验证（Jenkins）](https://github.com/gycrosskit/.github/blob/main/docs/统一远程验证.md)，按已推送源码 ref/SHA 选择对应组件；平台命令与参数以受信 Jenkins recipe 为准。实际执行范围、测试数量、结果与耗时以绑定源码/CI SHA 的构建回执为准，轻量检查或跳过不算平台测试通过。

新版本按[发布流程](https://github.com/gycrosskit/.github/blob/main/docs/发布流程.md)执行 `component-ci/release`。按渠道绑定准确的源码 SHA、版本和必要的 Tag，在同一次 Run 冻结候选并核对远端制品，保留发布和消费回执。

HAR 使用受管 HarmonyOS CLT 构建。KLIB 编译、Registry 安装及真机验收分别按实际回执记录。

阶段、缓存、有限网络重试、失败记录与证据边界见[共用 CI 规则](https://github.com/gycrosskit/.github/blob/main/docs/持续集成门禁.md)；本库实际平台命令以 Jenkins 的固定版本 recipe 为准。源码通过、远程消费、HAR/ohpm 与设备验收分别记录。

此版本统一图片解码输入最大 32 MiB（超限为 null/原生 invalid_content），原生预览默认识别区为 260 dp/points/vp；宿主显式 frame 参数仍优先。

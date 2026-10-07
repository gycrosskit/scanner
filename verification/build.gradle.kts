plugins {
    kotlin("multiplatform") version "2.2.21-1.0.0"
    id("com.android.library") version "8.10.1"
    kotlin("plugin.compose") version "2.2.21-1.0.0" apply false
}
val scannerVersion = providers.gradleProperty("scannerVersion").orElse("0.1.6").get()
val verifyUnifiedUi = providers.gradleProperty("verifyUnifiedUi").orElse("false").get().toBoolean()
val kuiklyRenderFrameworkDir = providers.gradleProperty("kuiklyRenderFrameworkDir").orNull
if (verifyUnifiedUi) apply(plugin = "org.jetbrains.kotlin.plugin.compose")
kotlin {
    androidTarget()
    iosArm64()
    iosX64 { binaries.framework {
        baseName = "ScannerConsumer"
        if (verifyUnifiedUi) kuiklyRenderFrameworkDir?.let { linkerOpts("-F$it", "-framework", "OpenKuiklyIOSRender") }
    } }
    iosSimulatorArm64 { binaries.framework {
        baseName = "ScannerConsumer"
        if (verifyUnifiedUi) kuiklyRenderFrameworkDir?.let { linkerOpts("-F$it", "-framework", "OpenKuiklyIOSRender") }
    } }
    ohosArm64()
    sourceSets {
        commonMain {
            if (!verifyUnifiedUi) kotlin.exclude("**/Unified*.kt")
            dependencies {
                implementation("com.github.gycrosskit.scanner:scanner-core:$scannerVersion")
                if (verifyUnifiedUi) implementation("com.github.gycrosskit.scanner:scanner-kuikly:$scannerVersion")
            }
        }
        ohosArm64Main.dependencies { implementation("com.github.gycrosskit.scanner:scanner-kuikly:$scannerVersion") }
    }
}
android { namespace = "io.github.gycrosskit.scanner.consumer"; compileSdk = 36; defaultConfig { minSdk = 24 } }

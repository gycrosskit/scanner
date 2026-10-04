plugins {
    kotlin("multiplatform") version "2.2.21-1.0.0"
    id("com.android.library") version "8.10.1"
}
val scannerVersion = providers.gradleProperty("scannerVersion").orElse("0.1.3").get()
kotlin {
    androidTarget()
    iosSimulatorArm64 { binaries.framework { baseName = "ScannerConsumer" } }
    ohosArm64()
    sourceSets {
        commonMain.dependencies { implementation("com.github.gycrosskit.scanner:scanner-core:$scannerVersion") }
        ohosArm64Main.dependencies { implementation("com.github.gycrosskit.scanner:scanner-kuikly:$scannerVersion") }
    }
}
android { namespace = "io.github.gycrosskit.scanner.consumer"; compileSdk = 36; defaultConfig { minSdk = 24 } }

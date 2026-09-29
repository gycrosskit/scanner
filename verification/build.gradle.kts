plugins {
    kotlin("multiplatform") version "2.2.21-1.0.0"
    id("com.android.library") version "8.10.1"
}
kotlin {
    androidTarget()
    iosSimulatorArm64 { binaries.framework { baseName = "ScannerConsumer" } }
    ohosArm64()
    sourceSets {
        commonMain.dependencies { implementation("com.github.gycrosskit.scanner:scanner-core:0.1.1") }
        ohosArm64Main.dependencies { implementation("com.github.gycrosskit.scanner:scanner-kuikly:0.1.1") }
    }
}
android { namespace = "io.github.gycrosskit.scanner.consumer"; compileSdk = 36; defaultConfig { minSdk = 24 } }

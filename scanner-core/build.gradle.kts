plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.library)
    `maven-publish`
}

kotlin {
    androidTarget {
        publishLibraryVariants("release")
        compilerOptions { jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_11) }
    }
    iosX64()
    iosArm64()
    iosSimulatorArm64()
    ohosArm64()
    sourceSets {
        androidMain.dependencies {
            api("com.journeyapps:zxing-android-embedded:4.3.0")
            implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.2")
        }
        androidUnitTest.dependencies { implementation("org.robolectric:robolectric:4.16") }
        commonTest.dependencies { implementation(kotlin("test")) }
    }
}

android {
    namespace = "io.github.gycrosskit.scanner"
    compileSdk = 36
    defaultConfig { minSdk = 24 }
    testOptions { unitTests.isIncludeAndroidResources = true }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

publishing {
    repositories { maven { name = "staging"; url = uri(rootProject.layout.buildDirectory.dir("maven")) } }
}

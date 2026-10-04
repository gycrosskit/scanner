plugins { kotlin("jvm") version "2.2.21-1.0.0" }
kotlin { compilerOptions.jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17) }
java { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
sourceSets.test {
    // 直接编译生产源码，Kuikly 传输替身仅在该独立测试工程存在。
    kotlin.srcDirs("../scanner-kuikly/src/commonMain/kotlin", "../scanner-core/src/commonMain/kotlin")
    kotlin.exclude("**/ScannerPreviewView.kt")
}
dependencies {
    testImplementation(kotlin("test-junit"))
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.10.2-1.0.0")
}

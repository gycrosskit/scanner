pluginManagement {
    repositories {
        maven("https://maven.eazytec-cloud.com/nexus/repository/maven-public/")
        google(); mavenCentral(); gradlePluginPortal()
        maven("https://mirrors.tencent.com/nexus/repository/maven-public/")
    }
}
dependencyResolutionManagement {
    repositories {
        maven("https://jitpack.io")
        maven("https://maven.eazytec-cloud.com/nexus/repository/maven-public/")
        google(); mavenCentral()
        maven("https://mirrors.tencent.com/nexus/repository/maven-public/")
        maven("https://mirrors.tencent.com/nexus/repository/maven-tencent/")
    }
}
rootProject.name = "scanner-consumer"

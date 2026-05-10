pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "tinytube"
include(":app")

plugins {
    // These versions are critical for Gradle 9.x compatibility
    id("com.android.application") version "8.7.0"
    id("org.jetbrains.kotlin.android") version "1.9.24"
}

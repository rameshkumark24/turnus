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

rootProject.name = "turnus"

// :data, :app and :widget are added when the Android modules land.
// :engine is pure Kotlin/JVM, so this builds and tests without the Android SDK.
include(":engine")

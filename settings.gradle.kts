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

// :engine is pure Kotlin/JVM and builds without the Android SDK.
include(":engine")

// :data owns Room. :app and :widget follow.
include(":data")

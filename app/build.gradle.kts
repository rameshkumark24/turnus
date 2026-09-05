plugins {
    // No kotlin-android: AGP 9 has built-in Kotlin and registers the `kotlin`
    // extension itself. The Compose compiler plugin is still applied separately.
    alias(libs.plugins.android.application)
    alias(libs.plugins.compose.compiler)
}

android {
    namespace = "com.turnus.rota"
    compileSdk = libs.versions.compileSdk.get().toInt()

    defaultConfig {
        applicationId = "com.turnus.rota"
        minSdk = libs.versions.minSdk.get().toInt()
        targetSdk = libs.versions.targetSdk.get().toInt()
        versionCode = 1
        versionName = "0.1.0"
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    buildTypes {
        debug {
            // Lets a debug build sit alongside a Play install on the same device.
            applicationIdSuffix = ".debug"
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

kotlin {
    jvmToolchain(17)
}

dependencies {
    implementation(project(":data"))

    implementation(libs.core.ktx)
    implementation(libs.work.runtime.ktx)
    implementation(libs.lifecycle.runtime.ktx)
    implementation(libs.activity.compose)

    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.graphics)
    implementation(libs.compose.foundation)
    implementation(libs.compose.material3)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.lifecycle.viewmodel.compose)
    implementation(libs.lifecycle.runtime.compose)
    debugImplementation(libs.compose.ui.tooling)

    testImplementation(libs.kotlin.test.junit)
}

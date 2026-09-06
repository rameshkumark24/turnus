plugins {
    // No kotlin-android: AGP 9 has built-in Kotlin support.
    alias(libs.plugins.android.library)
    alias(libs.plugins.compose.compiler)
}

android {
    namespace = "com.turnus.rota.widget"
    compileSdk = libs.versions.compileSdk.get().toInt()

    defaultConfig {
        minSdk = libs.versions.minSdk.get().toInt()
    }

    buildFeatures {
        compose = true
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
    // Glance brings its own Compose runtime for the widget's RemoteViews
    // translation; the app's Compose UI artifacts are deliberately not here.
    implementation(libs.glance.appwidget)
    implementation(libs.glance.material3)
}

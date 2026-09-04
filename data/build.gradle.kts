plugins {
    // AGP 9 has Kotlin support built in and registers the `kotlin` extension
    // itself — applying org.jetbrains.kotlin.android as well is a hard conflict.
    alias(libs.plugins.android.library)
    alias(libs.plugins.ksp)
}

android {
    namespace = "com.turnus.rota.data"
    compileSdk = libs.versions.compileSdk.get().toInt()

    defaultConfig {
        minSdk = libs.versions.minSdk.get().toInt()
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

kotlin {
    jvmToolchain(17)
}

// Room's exported schema JSON is what makes migration tests possible.
// Without it you are guessing about upgrade paths, and upgrade paths are the
// one thing here that cannot be rolled back.
ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

dependencies {
    api(project(":engine"))

    implementation(libs.core.ktx)
    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)

    testImplementation(libs.kotlin.test.junit)
    androidTestImplementation(libs.room.testing)
}

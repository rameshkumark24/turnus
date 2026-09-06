import java.util.Properties

plugins {
    // No kotlin-android: AGP 9 has built-in Kotlin and registers the `kotlin`
    // extension itself. The Compose compiler plugin is still applied separately.
    alias(libs.plugins.android.application)
    alias(libs.plugins.compose.compiler)
}

/**
 * AdMob identifiers, read from `local.properties` — which is gitignored.
 *
 * They are not secrets: an app's ad unit ids are visible to anyone who unzips
 * the APK. Keeping them out of the repository is about not publishing one
 * developer's revenue identifiers in a public repo, and about the release build
 * refusing to quietly ship with test units earning nothing.
 *
 * Google's public test ids are the fallback. A debug build always uses them —
 * CLAUDE.md forbids testing against live units, because impressions from a
 * developer's own device are what gets an AdMob account suspended.
 */
val localProperties = Properties().apply {
    rootProject.file("local.properties").takeIf { it.exists() }?.inputStream()?.use(::load)
}

fun adId(key: String, test: String): String =
    localProperties.getProperty(key)?.takeIf { it.isNotBlank() } ?: test

// Documented at developers.google.com/admob/android/test-ads
val testAppId = "ca-app-pub-3940256099942544~3347511713"
val testBannerId = "ca-app-pub-3940256099942544/9214589741"

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
            // Always the test units, never the real ones. Clicking your own
            // live ads while developing is how an AdMob account gets suspended.
            manifestPlaceholders["admobAppId"] = testAppId
            buildConfigField("String", "AD_BANNER_UNIT_ID", "\"$testBannerId\"")
            // The hashed id UMP logs on first run. Without it, setDebugGeography
            // is ignored on a real device and the EEA consent form — the one
            // that most needs testing — can never be seen from outside the EEA.
            buildConfigField(
                "String",
                "AD_TEST_DEVICE_ID",
                "\"${localProperties.getProperty("admob.testDeviceId").orEmpty()}\"",
            )
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            manifestPlaceholders["admobAppId"] = adId("admob.appId", testAppId)
            buildConfigField("String", "AD_TEST_DEVICE_ID", "\"\"")
            buildConfigField(
                "String",
                "AD_BANNER_UNIT_ID",
                "\"${adId("admob.bannerUnitId", testBannerId)}\"",
            )
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
    implementation(project(":widget"))

    implementation(libs.core.ktx)
    implementation(libs.work.runtime.ktx)
    implementation(libs.play.services.ads)
    implementation(libs.user.messaging.platform)
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

// Visible on every build rather than discovered after a release ships earning
// nothing: without these keys the release build falls back to test units.
if (localProperties.getProperty("admob.appId").isNullOrBlank()) {
    logger.lifecycle(
        "Turnus: no admob.appId in local.properties - release builds will use " +
            "Google's TEST ad units and earn nothing.",
    )
}

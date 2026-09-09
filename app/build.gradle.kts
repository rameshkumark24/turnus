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
val testNativeId = "ca-app-pub-3940256099942544/2247696110"

android {
    namespace = "com.turnus.rota"
    compileSdk = libs.versions.compileSdk.get().toInt()

    defaultConfig {
        applicationId = "com.turnus.rota"
        minSdk = libs.versions.minSdk.get().toInt()
        targetSdk = libs.versions.targetSdk.get().toInt()
        // Increases on every upload, and never repeats — Play rejects a code it
        // has seen, including from a bundle that was deleted.
        versionCode = 1
        // 1.0.0 rather than 0.1.0: this goes to internal testing as a release
        // candidate, not as a preview. There is no feature here waiting to
        // arrive before the app is worth its first whole number.
        versionName = "1.0.0"
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    /**
     * Release signing, from `local.properties` or the environment.
     *
     * Nothing here names a file inside the repository, and the keystore itself
     * is covered by `.gitignore`. A signing key committed once is committed
     * forever — the history keeps it after the file is deleted — and for a Play
     * app that means anyone can publish an update as you.
     *
     * The environment variables exist so a CI runner can sign without a
     * `local.properties`; a developer machine uses the file. When neither is
     * present the release build is left unsigned rather than failing, so
     * `assembleRelease` still works for checking that R8 has not broken
     * anything, which is what it is mostly used for here. A missing key only
     * ever warns, because an unsigned bundle cannot be uploaded — the failure
     * announces itself at the upload screen. Missing *ad ids* are the opposite
     * and do fail the bundle; see the bottom of this file for why the two are
     * treated differently.
     */
    val keystoreFile = (
        localProperties.getProperty("release.keystore")
            ?: System.getenv("TURNUS_KEYSTORE")
        )?.let(::file)?.takeIf(File::exists)

    val keystorePassword = localProperties.getProperty("release.keystorePassword")
        ?: System.getenv("TURNUS_KEYSTORE_PASSWORD")
    val keyAlias = localProperties.getProperty("release.keyAlias")
        ?: System.getenv("TURNUS_KEY_ALIAS")
    val keyPassword = localProperties.getProperty("release.keyPassword")
        ?: System.getenv("TURNUS_KEY_PASSWORD")

    val canSign = keystoreFile != null &&
        !keystorePassword.isNullOrBlank() &&
        !keyAlias.isNullOrBlank() &&
        !keyPassword.isNullOrBlank()

    signingConfigs {
        if (canSign) {
            create("release") {
                storeFile = keystoreFile
                storePassword = keystorePassword
                this.keyAlias = keyAlias
                this.keyPassword = keyPassword
            }
        }
    }

    buildTypes {
        debug {
            // Lets a debug build sit alongside a Play install on the same device.
            applicationIdSuffix = ".debug"
            // Always the test units, never the real ones. Clicking your own
            // live ads while developing is how an AdMob account gets suspended.
            manifestPlaceholders["admobAppId"] = testAppId
            buildConfigField("String", "AD_BANNER_UNIT_ID", "\"$testBannerId\"")
            buildConfigField("String", "AD_NATIVE_UNIT_ID", "\"$testNativeId\"")
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
            // Null when no key is configured: the build stays unsigned rather
            // than failing, so R8 can still be exercised on any machine.
            signingConfig = signingConfigs.findByName("release")
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
            buildConfigField(
                "String",
                "AD_NATIVE_UNIT_ID",
                "\"${adId("admob.nativeUnitId", testNativeId)}\"",
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

// The same warning for signing. An unsigned bundle cannot be uploaded to Play,
// and finding that out at the upload screen wastes a four-minute build.
if (localProperties.getProperty("release.keystore").isNullOrBlank() &&
    System.getenv("TURNUS_KEYSTORE").isNullOrBlank()
) {
    logger.lifecycle(
        "Turnus: no release.keystore in local.properties - release builds will " +
            "be UNSIGNED and cannot be uploaded to Play. See docs/RELEASING.md.",
    )
}

/**
 * A bundle may not be built with Google's test ad units.
 *
 * The warning above is not enough for this one. Every other way a release build
 * can be wrong announces itself: an unsigned bundle is rejected at the upload
 * screen, a bad migration crashes, a broken R8 rule throws. Shipping test ad
 * units announces nothing at all. The app installs, runs, and fills 100% of its
 * ad requests — with Google's demo creatives, for no money — and the first
 * symptom is an AdMob dashboard reading zero a week later, by which time the
 * bundle is live and the fix needs another release.
 *
 * With one banner as the entire business, that failure costs everything and
 * looks like success, so `bundleRelease` refuses rather than warns.
 *
 * Only the bundle. `assembleRelease` keeps the fallback, because it is what
 * `docs/RELEASING.md` tells you to run to prove R8 has not broken anything and
 * it has to work on a machine that has no `local.properties` — including a fresh
 * clone, CI, and whoever picks this up next.
 */
tasks.matching { it.name == "bundleRelease" }.configureEach {
    doFirst {
        val configured = localProperties.getProperty("admob.appId").orEmpty()
        check(configured.isNotBlank() && configured != testAppId) {
            buildString {
                appendLine("Refusing to bundle with Google's TEST ad units.")
                appendLine()
                appendLine("This bundle would install, run, and serve demo adverts for no money,")
                appendLine("with nothing to tell you until the dashboard reads zero.")
                appendLine()
                appendLine("Put your real ids in local.properties:")
                appendLine("  admob.appId=ca-app-pub-XXXXXXXXXXXXXXXX~YYYYYYYYYY")
                appendLine("  admob.bannerUnitId=ca-app-pub-XXXXXXXXXXXXXXXX/YYYYYYYYYY")
                appendLine("  admob.nativeUnitId=ca-app-pub-XXXXXXXXXXXXXXXX/YYYYYYYYYY")
                appendLine()
                append("To exercise R8 without them, build assembleRelease instead.")
            }
        }
    }
}

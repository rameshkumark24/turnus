plugins {
    alias(libs.plugins.kotlin.jvm)
}

// :engine is deliberately pure Kotlin/JVM.
// It must never gain an Android dependency — that is what keeps its property
// tests running in milliseconds without an emulator.
kotlin {
    jvmToolchain(17)
}

dependencies {
    testImplementation(kotlin("test"))
}

tasks.test {
    useJUnitPlatform()
    testLogging {
        events("passed", "skipped", "failed")
    }
}

buildscript {
    dependencies {
        // Keep AGP built-in Kotlin aligned with the Compose compiler.
        classpath(libs.kotlin.gradle.plugin)
    }
}

// Top-level build file where you can add configuration options common to all sub-projects/modules.
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.hilt) apply false
}

/** Single entry point for every local test; no Android device or emulator is required. */
abstract class RunAllTests : DefaultTask() {
    init {
        group = "verification"
        description = "Runs all module unit tests locally, without an Android device or emulator."
        dependsOn(
            ":app:testDebugUnitTest",
            ":core:testDebugUnitTest",
            ":compose-ui:testDebugUnitTest",
        )
    }
}

tasks.register<RunAllTests>("runAllTests")

val runAllTestsRequested = gradle.startParameter.taskNames.any {
    it.substringAfterLast(':') == "runAllTests"
}

subprojects {
    tasks.withType<Test>().configureEach {
        if (runAllTestsRequested) {
            // Re-execute tests from the single entry point while retaining compilation caches.
            outputs.upToDateWhen { false }
        }
    }
}


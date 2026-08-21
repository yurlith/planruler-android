plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.compose.compiler) apply false
    alias(libs.plugins.android.library) apply false
}

// Gradle 8.7 cannot hash connected-test result paths when the workspace contains Cyrillic.
// Instrumentation tasks are side-effectful device runs anyway, so incremental tracking is invalid for them.
subprojects {
    tasks.configureEach {
        if (name.startsWith("connected") && name.endsWith("AndroidTest")) {
            doNotTrackState("Connected device results are not cacheable in this Unicode workspace")
        }
    }
}

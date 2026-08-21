plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.compose.compiler)
}

android {
    namespace = "com.planruler.feature.pipecalculator"
    compileSdk = 36
    defaultConfig { minSdk = 26 }
    buildFeatures { compose = true }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
}

dependencies {
    implementation(project(":core:model"))
    implementation(project(":core:project-api"))
    implementation(project(":core:designsystem"))
    implementation(project(":core:pipe-calculator"))
    implementation(project(":core:fabrication3d-api"))
    implementation(project(":core:fabrication3d-catalog"))
    implementation(platform(libs.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation("androidx.compose.animation:animation")
    implementation(libs.compose.ui)
    implementation(libs.compose.material3)
    implementation(libs.compose.ui.tooling.preview)
    testImplementation(libs.junit)
    // Tests compose the real engine, exactly as the app's composition root does.
    testImplementation(project(":core:fabrication3d-engine"))
    testImplementation(libs.kotlinx.coroutines.test)
}

import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.compose.compiler)
}

val keystoreProperties = Properties()
val keystorePropertiesFile = rootProject.file("keystore.properties")
if (keystorePropertiesFile.isFile) {
    keystorePropertiesFile.inputStream().use(keystoreProperties::load)
}
fun signingValue(property: String, environment: String): String? =
    (keystoreProperties.getProperty(property) ?: System.getenv(environment))
        ?.trim()
        ?.takeIf(String::isNotEmpty)

val releaseStoreFile = signingValue("storeFile", "PLANRULER_KEYSTORE_FILE")
val releaseStorePassword = signingValue("storePassword", "PLANRULER_KEYSTORE_PASSWORD")
val releaseKeyAlias = signingValue("keyAlias", "PLANRULER_KEY_ALIAS")
val releaseKeyPassword = signingValue("keyPassword", "PLANRULER_KEY_PASSWORD")
val hasReleaseSigning = listOf(
    releaseStoreFile,
    releaseStorePassword,
    releaseKeyAlias,
    releaseKeyPassword,
).all { it != null }

android {
    namespace = "com.planruler.app"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.planruler.app"
        minSdk = 26
        targetSdk = 36
        versionCode = 10500
        versionName = "1.5.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }
    signingConfigs {
        create("release") {
            if (hasReleaseSigning) {
                storeFile = rootProject.file(requireNotNull(releaseStoreFile))
                storePassword = requireNotNull(releaseStorePassword)
                keyAlias = requireNotNull(releaseKeyAlias)
                keyPassword = requireNotNull(releaseKeyPassword)
            }
        }
    }
    buildTypes {
        debug {
            isPseudoLocalesEnabled = true
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            if (hasReleaseSigning) signingConfig = signingConfigs.getByName("release")
        }
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    sourceSets["main"].java.setSrcDirs(listOf("src/new/kotlin"))
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
}

dependencies {
    implementation(project(":core:model"))
    implementation(project(":core:designsystem"))
    implementation(project(":core:engine-api"))
    implementation(project(":core:engine-default"))
    implementation(project(":core:document-api"))
    implementation(project(":core:document-android"))
    implementation(project(":core:project-api"))
    implementation(project(":core:project-local"))
    implementation(project(":core:crm-api"))
    implementation(project(":core:crm-local"))
    implementation(project(":core:backup"))
    implementation(project(":core:export-api"))
    implementation(project(":core:export-android"))
    implementation(project(":core:fabrication3d-api"))
    implementation(project(":core:fabrication3d-engine"))
    implementation(project(":feature:projects"))
    implementation(project(":feature:workspace"))
    implementation(project(":feature:settings"))
    implementation(project(":feature:pipe-calculator"))
    implementation(project(":feature:crm"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)

    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.material3)
    implementation(libs.compose.ui.tooling.preview)
    debugImplementation(libs.compose.ui.tooling)
    debugImplementation(libs.compose.ui.test.manifest)
    androidTestImplementation(platform(libs.compose.bom))
    androidTestImplementation(libs.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.test.junit)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.test.uiautomator)
    androidTestImplementation(libs.androidx.exifinterface)
}

val verifyOfflinePrivacy = tasks.register("verifyOfflinePrivacy") {
    group = "verification"
    val manifest = file("src/main/AndroidManifest.xml")
    inputs.file(manifest)
    doLast {
        val xml = manifest.readText(Charsets.UTF_8)
        check("android.permission.INTERNET" !in xml) { "Offline release must not request INTERNET permission" }
        check("android:allowBackup=\"false\"" in xml) { "Android cloud Auto Backup must stay disabled" }
        check("android:fullBackupContent=\"false\"" in xml) { "Full cloud backup must stay disabled" }
    }
}

tasks.named("check").configure { dependsOn(verifyOfflinePrivacy) }

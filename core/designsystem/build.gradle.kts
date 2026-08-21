plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.compose.compiler)
}
android {
    namespace = "com.planruler.designsystem"
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
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.material3)
    implementation(libs.compose.ui.tooling.preview)
    testImplementation(libs.junit)
}

val verifyTranslationCoverage = tasks.register("verifyTranslationCoverage") {
    group = "verification"
    description = "Fails when a static UI string is missing in any release language."
    val kotlinSources = rootProject.fileTree(rootProject.projectDir) {
        include("**/src/main/**/*.kt")
        exclude("**/build/**", "**/bin/**", "**/GeneratedTranslations.kt")
    }
    val catalog = file("src/main/kotlin/com/planruler/designsystem/localization/GeneratedTranslations.kt")
    inputs.files(kotlinSources, catalog)
    doLast {
        fun decoded(raw: String): String = raw
            .replace("\\\"", "\"")
            .replace("\\n", "\n")
            .replace("\\t", "\t")
            .replace("\\\\", "\\")
        val pair = Regex("""(?s)\bt\(\s*"((?:\\.|[^"\\])*)"\s*,\s*"((?:\\.|[^"\\])*)"\s*\)""")
        val direct = Regex("""(?s)localizedUi\(\s*[^,]+,\s*"((?:\\.|[^"\\])*)"\s*,\s*"((?:\\.|[^"\\])*)"\s*\)""")
        val required = linkedSetOf<String>()
        kotlinSources.files.forEach { source ->
            val text = source.readText(Charsets.UTF_8)
            pair.findAll(text).forEach { match ->
                match.groupValues[2].takeUnless { '$' in it }?.let { required += decoded(it) }
            }
            direct.findAll(text).forEach { match ->
                match.groupValues[2].takeUnless { '$' in it }?.let { required += decoded(it) }
            }
        }
        val generated = catalog.readText(Charsets.UTF_8)
        listOf("German", "Polish", "French", "Italian").forEach { language ->
            val block = Regex(
                """(?s)internal val generated${language}Ui.*?= mapOf\((.*?)\n\)""",
            ).find(generated)?.groupValues?.get(1)
                ?: error("Generated $language catalogue is missing")
            val available = Regex("""(?m)^\s*"((?:\\.|[^"\\])*)"\s+to\s+"((?:\\.|[^"\\])*)"""")
                .findAll(block)
                .associate { decoded(it.groupValues[1]) to decoded(it.groupValues[2]) }
            val missing = required - available.keys
            check(missing.isEmpty()) {
                "$language is missing ${missing.size} UI translations:\n${missing.sorted().joinToString("\n")}"
            }
            val blank = available.filterValues(String::isBlank).keys
            check(blank.isEmpty()) { "$language contains blank translations: $blank" }
        }
    }
}

tasks.named("check").configure { dependsOn(verifyTranslationCoverage) }

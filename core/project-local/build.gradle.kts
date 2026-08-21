plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
}
dependencies {
    implementation(project(":core:model"))
    implementation(project(":core:project-api"))
    implementation(libs.kotlinx.serialization.json)
    testImplementation(libs.kotlinx.coroutines.core)
    testImplementation(libs.junit)
}
kotlin { compilerOptions { jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17) } }
java { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }

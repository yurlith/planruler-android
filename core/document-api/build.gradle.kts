plugins { alias(libs.plugins.kotlin.jvm) }
dependencies {
    api(project(":core:model"))
    testImplementation(libs.junit)
}
kotlin { compilerOptions { jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17) } }
java { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }

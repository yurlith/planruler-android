pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "PlanRuler"
include(
    ":app",
    ":core:model",
    ":core:designsystem",
    ":core:engine-api",
    ":core:engine-default",
    ":core:document-api",
    ":core:document-android",
    ":core:project-api",
    ":core:project-local",
    ":core:pipe-calculator",
    ":core:fabrication3d-api",
    ":core:fabrication3d-engine",
    ":core:fabrication3d-catalog",
    ":core:crm-api",
    ":core:crm-local",
    ":core:backup",
    ":core:export-api",
    ":core:export-android",
    ":feature:projects",
    ":feature:workspace",
    ":feature:settings",
    ":feature:pipe-calculator",
    ":feature:crm",
)

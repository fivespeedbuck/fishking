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

rootProject.name = "FishKing"

include(
    ":app",
    ":core:model",
    ":core:database",
    ":core:location",
    ":core:media",
    ":core:reminder",
    ":core:ui",
    ":core:usecase",
    ":feature:home",
    ":feature:journal",
    ":feature:habit",
    ":feature:life",
)

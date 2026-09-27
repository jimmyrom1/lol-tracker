pluginManagement {
    includeBuild("build-logic")
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

rootProject.name = "lol-tracker"
enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")

include(":app")
include(":core:model")
include(":core:domain")
include(":core:database")
include(":core:network")
include(":core:data")
include(":core:designsystem")
include(":core:analytics")
include(":core:testing")
include(":feature:matches")
include(":feature:stats")
include(":feature:profile")
include(":feature:draft")

pluginManagement {
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
    plugins {
        id("com.google.gms.google-services") version "4.4.2"
    }
}

// Note: dependencyResolutionManagement је означен као @Incubating (експериментални),
// али је стандардан приступ у модерним Gradle пројектима за управљање зависностима.
// Упозорење се може игнорисати док функција не постане стабилна у будућим верзијама Gradle-а.
@Suppress("UnstableApiUsage")
dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "Smrdici"
include(":app")
 
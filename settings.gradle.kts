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
}

// Note: dependencyResolutionManagement је означен као @Incubating (експериментални),
// али је стандардан приступ у модерним Gradle пројектима за управљање зависностима.
// Упозорење се може игнорисати док функција не постане стабилна у будућим верзијама Gradle-а.
dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "Smrdici"
include(":app")
 
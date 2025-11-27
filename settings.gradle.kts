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

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()

        // Repositorio donde realmente está epublib-core
        maven {
            url = uri("https://raw.githubusercontent.com/psiegman/mvn-repo/master/releases")
        }

        maven {
            url = uri("https://jitpack.io")
        }
    }
}

rootProject.name = "LectorNovelasElectronicos"
include(":app")

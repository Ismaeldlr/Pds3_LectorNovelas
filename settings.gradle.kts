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
        // Required for nl.siegmann.epublib artifacts used by the EPUB importer
        maven(url = "https://jcenter.bintray.com/")
        maven(url = "https://jitpack.io")
    }
}

rootProject.name = "LectorNovelasElectronicos"
include(":app")
 
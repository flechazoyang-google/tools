pluginManagement {
    repositories {
        google {
            content {
                includeGroupByRegex(".*androidx.*")
                includeGroupByRegex(".*com\\.android.*")
            }
        }
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

rootProject.name = "Toolbox"
include(":app")

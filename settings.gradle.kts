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
    }
}

rootProject.name = "DeskBreak"
// :app = Uhr, :mobile = Handy-App, :shared = Sync-Protokoll und Lizenzregeln beider Apps,
// :billing = Google Play Billing beider Apps.
include(":app")
include(":shared")
include(":billing")
include(":mobile")
include(":baselineprofile")

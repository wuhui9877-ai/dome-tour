pluginManagement {
    repositories {
        // Local Maven mirror (populated by ../mirror-deps.py). First, so
        // --offline builds on this VM resolve without Java networking.
        // Harmless elsewhere: it simply does not exist on other machines.
        maven { url = uri(layout.settingsDirectory.dir("../maven-mirror")) }
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
        gradlePluginPortal()
        maven {
            url = uri("https://artifact.bytedance.com/repository/Volcengine")
            name = ""
        }
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        // Local Maven mirror first; see pluginManagement above.
        maven { url = uri(layout.settingsDirectory.dir("../maven-mirror")) }
        google()
        mavenCentral()
        maven {
            url = uri("https://artifact.bytedance.com/repository/Volcengine")
            name = ""
        }
    }
}

rootProject.name = "DomeTour"
include(":app")

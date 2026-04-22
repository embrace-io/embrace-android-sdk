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
        maven {
            name = "centralManualTesting"
            url = uri("<path-to-bundle>")
            content {
                includeGroup("io.embrace")
                includeGroup("io.embrace.gradle")
            }
        }

        mavenLocal()
        gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        maven {
            name = "centralManualTesting"
            url = uri("<path-to-bundle>")
            content {
                includeGroup("io.embrace")
                includeGroup("io.embrace.gradle")
            }
        }
        mavenLocal()
    }
}

rootProject.name = "ExampleApp"
include(":app")
include(":app:benchmark")

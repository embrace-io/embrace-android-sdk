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
        mavenLocal {
            content {
                includeGroup("io.embrace")
                includeGroup("io.embrace.gradle")
            }
        }
        gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        mavenLocal {
            content {
                includeGroup("io.embrace")
            }
        }
    }
}

rootProject.name = "ExampleApp"
include(":app")
include(":app:benchmark")

// Shared with the SDK build's macrobenchmark, so included from the repo root.
include(":embrace-benchmark-endpoint")
project(":embrace-benchmark-endpoint").projectDir = file("../../embrace-benchmark-endpoint")
include(":embrace-test-api-server")
project(":embrace-test-api-server").projectDir = file("../../embrace-test-api-server")

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
        mavenLocal()
        gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        mavenLocal()
    }
}

rootProject.name = "ExampleApp"
include(":app")
include(":app:benchmark")

// the endpoint the benchmarks serve on the device, shared with the SDK build's macrobenchmark
include(":benchmark-endpoint")
project(":benchmark-endpoint").projectDir = settingsDir.resolve("../../embrace-benchmark-endpoint")

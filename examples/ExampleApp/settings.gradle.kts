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

// The endpoint the benchmarks serve on the device and the fake of Embrace's server backend it runs, shared with the SDK
// build's macrobenchmark. They live in the SDK build's directory, outside this build's, where include() looks for a project by
// default, so each is pointed at its directory, under the path it has in the SDK build.
listOf("embrace-benchmark-endpoint", "embrace-test-api-server").forEach { name ->
    include(":$name")
    project(":$name").projectDir = settingsDir.resolve("../../$name")
}

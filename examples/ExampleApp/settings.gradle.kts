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
        maven {  // add this with the swazzler repo
            url = uri("https://s01.oss.sonatype.org/content/repositories/ioembrace-1443")
        }
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        mavenLocal()
        maven {  // add this with the sdk repo
            url = uri("https://s01.oss.sonatype.org/content/repositories/ioembrace-1442")
        }
    }
}

rootProject.name = "ExampleApp"
include(":app")

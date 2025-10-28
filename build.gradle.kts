import org.jetbrains.dokka.gradle.DokkaTaskPartial

plugins {
    kotlin("android") apply false
    kotlin("jvm") apply false
    alias(libs.plugins.google.ksp) apply false
    id("com.android.library") apply false
    alias(libs.plugins.dokka)
    alias(libs.plugins.kover)
}

group = "io.embrace"
version = project.version

subprojects {
    if (project.name == "embrace-android-sdk" || project.name == "embrace-android-api") {
        apply(plugin = "org.jetbrains.dokka")
    }

    tasks.withType<DokkaTaskPartial>().configureEach {
        dokkaSourceSets.configureEach {
            perPackageOption {
                skipDeprecated.set(false)
                reportUndocumented.set(true) // Emit warnings about not documented members
                includeNonPublic.set(false)

                // Suppress files in the internal package
                matchingRegex.set(".*.internal.*?")
                suppress.set(true)
            }
            suppressObviousFunctions.set(true)
            noAndroidSdkLink.set(false)
        }
    }
}


kover {
    merge {
        subprojects { project ->
            val ignoreList = listOf("embrace-lint", "embrace-microbenchmark")
            !project.name.contains("-test") &&
                !project.name.contains("-fakes") &&
                !ignoreList.contains(project.name)
        }
    }
    reports {
        filters {
            excludes {
                androidGeneratedClasses()
                classes("*.BuildConfig")
            }
        }
    }
}

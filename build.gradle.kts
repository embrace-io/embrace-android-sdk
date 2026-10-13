import kotlinx.kover.gradle.plugin.dsl.KoverProjectExtension

plugins {
    kotlin("jvm") apply false
    alias(libs.plugins.google.ksp) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    id("com.android.library") apply false
    id("org.jetbrains.dokka")
}

group = "io.embrace"
version = project.version

// Kover instruments every class in every test JVM, so it is only applied on CI where coverage is reported.
if (providers.environmentVariable("CI").isPresent) {
    apply(plugin = "org.jetbrains.kotlinx.kover")
    configure<KoverProjectExtension> {
        merge {
            subprojects { project ->
                val ignoreList = listOf(
                    "embrace-lint",
                    "embrace-perfetto-analysis",
                    "embrace-microbenchmark",
                    "embrace-macrobenchmark",
                    "embrace-macrobenchmark-app",
                    "embrace-benchmark-common",
                )
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
}

tasks.register("verify") {
    group = "verification"
    description = "Runs static analysis and unit tests, with one Gradle plugin integration test as a sanity check."
    val pluginIntegrationTests = ":embrace-gradle-plugin-integration-tests"
    dependsOn(subprojects.filter { it.path != pluginIntegrationTests }.map { "${it.path}:check" })
    dependsOn("$pluginIntegrationTests:detekt", "$pluginIntegrationTests:validatePlugins", "$pluginIntegrationTests:sanityTest")
}

dependencies {
    dokka(project(":embrace-android-api"))
    dokka(project(":embrace-android-sdk"))
    dokka(project(":embrace-android-otel-java"))
}

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
val koverEnabled = providers.environmentVariable("CI").isPresent
val koverIgnoreList = listOf(
    "embrace-lint",
    "embrace-perfetto-analysis",
    "embrace-microbenchmark",
    "embrace-macrobenchmark",
    "embrace-macrobenchmark-app",
    "embrace-benchmark-common",
)

fun Project.hasCoverage() = !name.contains("-test") && !name.contains("-fakes") && !koverIgnoreList.contains(name)

if (koverEnabled) {
    apply(plugin = "org.jetbrains.kotlinx.kover")
    configure<KoverProjectExtension> {
        merge {
            subprojects { project -> project.hasCoverage() }
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

tasks.register("ciBuild") {
    group = "verification"
    description = "Runs everything the CI build job checks. CI adds the -x exclusions and -Pembrace.coverage."
    dependsOn(":build", subprojects.map { "${it.path}:build" })
    if (koverEnabled) {
        dependsOn(":koverXmlReport")
        dependsOn(subprojects.filter { it.hasCoverage() }.map { "${it.path}:koverXmlReport" })
    }
    dependsOn(
        ":embrace-microbenchmark:compileReleaseAndroidTestKotlin",
        ":embrace-macrobenchmark:compileBenchmarkKotlin",
        ":embrace-macrobenchmark-app:compileBenchmarkKotlin",
    )
    // These apps keep lint out of `check` because it runs lintAnalyzeRelease on every library.
    dependsOn(":embrace-minified-test-app:lint", ":embrace-microbenchmark:lint", ":embrace-macrobenchmark-app:lint")
}

dependencies {
    dokka(project(":embrace-android-api"))
    dokka(project(":embrace-android-sdk"))
    dokka(project(":embrace-android-otel-java"))
}

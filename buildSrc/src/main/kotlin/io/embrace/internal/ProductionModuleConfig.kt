package io.embrace.internal

import com.android.build.api.dsl.LibraryExtension
import org.gradle.api.Project
import org.gradle.api.artifacts.VersionCatalogsExtension
import org.gradle.kotlin.dsl.getByType

fun Project.configureProductionModule(
    android: LibraryExtension,
    module: EmbraceBuildLogicExtension
) {
    with(project.pluginManager) {
        apply("checkstyle")
        apply("org.jetbrains.kotlinx.kover")
        apply("maven-publish")
        apply("signing")
        apply("binary-compatibility-validator")
    }

    project.configureBinaryCompatValidation(module)

    android.apply {
        useLibrary("android.test.runner")
        useLibrary("android.test.base")
        useLibrary("android.test.mock")

        defaultConfig {
            testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
            aarMetadata.minCompileSdk = 34
        }

        configureTestOptions(this)

        buildTypes {
            named("release") {
                isMinifyEnabled = false
            }
        }

        publishing {

            // create component with single publication variant
            // https://developer.android.com/studio/publish-library/configure-pub-variants#single-pub-var
            singleVariant("release") {
                withSourcesJar()
                withJavadocJar()
            }
        }

        sourceSets {
            getByName("test").java.srcDir("src/integrationTest/java")
            getByName("test").kotlin.srcDir("src/integrationTest/kotlin")
            getByName("test").resources.srcDir("src/integrationTest/resources")
        }
    }

    configureCheckstyle()

    project.dependencies.apply {
        add("implementation", findLibrary("kotlin.stdlib"))
        add("lintChecks", project.project(":embrace-lint"))

        add("testImplementation", findLibrary("junit"))
        add("testImplementation", findLibrary("mockk"))
        add("testImplementation", findLibrary("androidx.test.core"))
        add("testImplementation", findLibrary("androidx.test.junit"))
        add("testImplementation", findLibrary("robolectric"))
        add("testImplementation", findLibrary("mockwebserver"))
        add("testImplementation", project(":embrace-test-common"))
        add("testImplementation", project(":embrace-test-fakes"))

        add("androidTestImplementation", findLibrary("androidx.test.core"))
        add("androidTestImplementation", findLibrary("androidx.test.runner"))
        add("androidTestUtil", findLibrary("androidx.test.orchestrator"))

        add("kover", project)
    }

    project.afterEvaluate {
        if (module.productionModule.get()) {
            configurePublishing()
        }
    }
}

// workaround: see https://medium.com/@saulmm2/android-gradle-precompiled-scripts-tomls-kotlin-dsl-df3c27ea017c
fun Project.findLibrary(alias: String) =
    project.extensions.getByType<VersionCatalogsExtension>().named("libs").findLibrary(alias).get()

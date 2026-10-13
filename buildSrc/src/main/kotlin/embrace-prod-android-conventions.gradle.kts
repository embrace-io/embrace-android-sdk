import kotlinx.kover.gradle.plugin.dsl.KoverProjectExtension

plugins {
    id("embrace-common-conventions")
    id("embrace-android-conventions")
    id("embrace-publishing-conventions")
}

android {
    useLibrary("android.test.runner")
    useLibrary("android.test.base")
    useLibrary("android.test.mock")

    defaultConfig {
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        aarMetadata {
            minCompileSdk = project.findVersion("minCompileSdk").toInt()
            minAgpVersion = project.findVersion("minAgpVersion")
        }
    }

    buildTypes {
        named("release") {
            isMinifyEnabled = false
        }
    }

    sourceSets {
        with(getByName("test")) {
            java.directories.add("src/integrationTest/java")
            kotlin.directories.add("src/integrationTest/kotlin")
            resources.directories.add("src/integrationTest/resources")
        }
    }
}

dependencies {
    add("testImplementation", findLibrary("junit"))
    add("testImplementation", findLibrary("androidx.test.core"))
    add("testImplementation", findLibrary("androidx.test.junit"))
}

// Kover is only applied on CI by the root project
pluginManager.withPlugin("org.jetbrains.kotlinx.kover") {
    configure<KoverProjectExtension> {
        currentProject {
            instrumentation {
                // Kover's agent slows tests - only attach when coverage is requested
                disabledForAll.set(!providers.gradleProperty("embrace.coverage").isPresent)
            }
        }
    }
}

import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("com.android.library")
    kotlin("android")
    alias(libs.plugins.benchmark)
}

android {
    namespace = "io.embrace.android.embracesdk.benchmark"
    compileSdk = 36

    defaultConfig {
        minSdk = 26
        testInstrumentationRunner = "androidx.benchmark.junit4.AndroidBenchmarkRunner"
        testInstrumentationRunnerArguments["androidx.benchmark.dryRunMode.enable"] = "true"
    }

    testBuildType = "release"

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    kotlin {
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_11)
        }
    }

    buildTypes {
        release {
            isDefault = true
            isMinifyEnabled = false
        }
    }
}

dependencies {
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.test.junit)
    androidTestImplementation(libs.junit)
    androidTestImplementation(libs.androidx.benchmark.junit4)
    androidTestImplementation(libs.opentelemetry.kotlin)
    androidTestImplementation(libs.opentelemetry.kotlin.compat)
    androidTestImplementation(project(":embrace-android-sdk"))
    androidTestImplementation(project(":embrace-android-core"))
    androidTestImplementation(project(":embrace-android-otel"))
    androidTestImplementation(project(":embrace-android-infra"))
    androidTestImplementation(project(":embrace-android-payload"))
}

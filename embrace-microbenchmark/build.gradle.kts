import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("com.android.library")
    alias(libs.plugins.benchmark)
}

android {
    namespace = "io.embrace.android.embracesdk.benchmark"
    compileSdk = 36

    defaultConfig {
        minSdk = 26
        testInstrumentationRunner = "androidx.benchmark.junit4.AndroidBenchmarkRunner"
    }

    testBuildType = "release"

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }

    buildTypes {
        release {
            isDefault = true
            isMinifyEnabled = false
        }
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_11)
    }
}

dependencies {
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.test.junit)
    androidTestImplementation(libs.junit)
    androidTestImplementation(libs.androidx.benchmark.junit4)
    androidTestImplementation(libs.opentelemetry.kotlin.api)
    androidTestImplementation(libs.opentelemetry.kotlin.compat)
    androidTestImplementation(platform(libs.okhttp.bom))
    androidTestImplementation(libs.okhttp)
    androidTestImplementation(project(":embrace-android-sdk"))
    androidTestImplementation(project(":embrace-android-core"))
    androidTestImplementation(project(":embrace-android-otel"))
    androidTestImplementation(project(":embrace-android-infra"))
    androidTestImplementation(project(":embrace-android-utils"))
    androidTestImplementation(project(":embrace-android-payload"))
    androidTestImplementation(project(":embrace-android-instrumentation-api"))
}

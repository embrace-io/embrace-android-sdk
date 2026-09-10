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
        testInstrumentationRunnerArguments["androidx.benchmark.suppressErrors"] = "EMULATOR,LOW-BATTERY"
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
    testOptions.unitTests.isReturnDefaultValues = true
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_11)
        optIn.add("io.opentelemetry.kotlin.ExperimentalApi")
    }
}

dependencies {
    implementation(libs.opentelemetry.kotlin.api)
    implementation(libs.opentelemetry.kotlin.compat)
    implementation(platform(libs.okhttp.bom))
    implementation(libs.okhttp)
    implementation(project(":embrace-android-sdk"))
    implementation(project(":embrace-android-core"))
    implementation(project(":embrace-android-otel"))
    implementation(project(":embrace-android-infra"))
    implementation(project(":embrace-android-utils"))
    implementation(project(":embrace-android-payload"))
    implementation(project(":embrace-android-session-persistence"))
    implementation(project(":embrace-android-instrumentation-api"))

    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.test.junit)
    androidTestImplementation(libs.junit)
    androidTestImplementation(libs.androidx.benchmark.junit4)

    testImplementation(libs.junit)
    testImplementation(project(":embrace-test-common"))
}

import org.jetbrains.kotlin.gradle.dsl.JvmTarget

// Embrace's endpoints served on the device, for the benchmarks of both apps that benchmark the SDK: this build's
// embrace-macrobenchmark-app and examples/ExampleApp, a separate build that includes this directory as its
// :benchmark-endpoint project. So this file must configure in both: plugins by id with no version (each build's
// root declares them), and only catalog entries both builds have.
//
// The ExampleApp benchmarks the SDK as published binaries, so this module must never depend on any module of the
// SDK build: it is the benchmarks' harness, and it is on their test APK's classpath only, not the measured app's.

plugins {
    id("com.android.library")
}

// both builds would otherwise share this directory's build/, and one build's outputs would be the other's stale ones
layout.buildDirectory.set(rootProject.layout.buildDirectory.dir("benchmark-endpoint"))

android {
    namespace = "io.embrace.android.embracesdk.benchmark.endpoint"
    compileSdk = 36

    defaultConfig {
        minSdk = 24
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_11)
    }
}

dependencies {
    // LocalEmbraceEndpointRule is a JUnit rule the benchmarks declare
    api(libs.junit)
}

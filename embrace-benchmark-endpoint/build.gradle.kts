import org.jetbrains.kotlin.gradle.dsl.JvmTarget

// Embrace's server backend API endpoints served on the device, for the benchmarks of both apps that benchmark the SDK:
// this build's embrace-macrobenchmark-app and examples/ExampleApp, a separate build that includes this directory under
// the same project path. So this file must configure in both: plugins by id with no version (each build's root
// declares them), and only catalog entries both builds have.
//
// The ExampleApp benchmarks the SDK as published binaries, so this module must never depend on any module of the
// SDK: its one project dependency, embrace-test-api-server, depends on none either. It is the benchmarks' harness, and
// it is on their test APK's classpath only, not the measured app's.

plugins {
    id("com.android.library")
}

// both builds would otherwise share this directory's build/, and one build's outputs would be the other's stale ones
layout.buildDirectory.set(rootProject.layout.buildDirectory.dir("benchmark-endpoint"))

android {
    namespace = "io.embrace.android.embracesdk.benchmark.endpoint"
    // both builds' (this build's catalog isn't the ExampleApp's): in the ExampleApp, MockWebServer brings the app's
    // OkHttp, whose okhttp-android requires 37
    compileSdk = 37

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

// The embrace-config.json of every app benchmarked against this endpoint, by its path in the repo, which a test
// checks gives the app a placeholder app ID and points its config and data endpoints here.
val benchmarkedConfigs = listOf(
    "examples/ExampleApp/app/src/benchmark/embrace-config.json",
    "examples/ExampleApp/app/src/benchmarkOtelRegular/embrace-config.json",
).map { projectDir.parentFile.resolve(it) }

tasks.withType<Test>().configureEach {
    systemProperty("benchmarkEndpoint.benchmarkedConfigs", benchmarkedConfigs.joinToString(File.pathSeparator))
    inputs.files(benchmarkedConfigs)
        .withPathSensitivity(PathSensitivity.RELATIVE)
        .withPropertyName("benchmarkedConfigs")
}

dependencies {
    api(project(":embrace-test-api-server"))
    // LocalEmbraceEndpointRule is a JUnit rule the benchmarks declare
    api(libs.junit)
}

import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.dsl.KotlinVersion

// A fake of Embrace's server backend API endpoints, served by OkHttp's MockWebServer, for everything that stands in for
// that backend: the SDK's integration tests, the Gradle plugin's integration tests and the benchmarks' endpoint served
// on the device.
// The benchmarks include a separate build, examples/ExampleApp, so this file must configure in it too: plugins by id
// with no version (each build's root declares them), only catalog entries both builds have, and none of this build's
// convention plugins.
//
// The ExampleApp benchmarks the SDK as published binaries, so this module must never depend on any module of the SDK.

plugins {
    id("org.jetbrains.kotlin.jvm")
}

// both builds would otherwise share this directory's build/, and one build's outputs would be the other's stale ones
layout.buildDirectory.set(rootProject.layout.buildDirectory.dir("test-api-server"))

java {
    sourceCompatibility = JavaVersion.VERSION_11
    targetCompatibility = JavaVersion.VERSION_11
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_11)
        allWarningsAsErrors.set(true)
    }
}

// The SDK build's modules run on an older Kotlin standard library than its compiler's, which its catalog names, and
// this module's would raise it on their test classpaths. The ExampleApp's catalog names none.
versionCatalogs.named("libs").findVersion("kotlinCoreLibrariesVersion").ifPresent { stdlib ->
    kotlin.coreLibrariesVersion = stdlib.requiredVersion
    kotlin.compilerOptions {
        apiVersion.set(KotlinVersion.fromVersion(stdlib.requiredVersion.substringBeforeLast('.')))
        freeCompilerArgs.add("-Xsuppress-version-warnings")
    }
}

dependencies {
    api(platform(libs.okhttp.bom))
    api(libs.mockwebserver)

    testImplementation(libs.junit)
}

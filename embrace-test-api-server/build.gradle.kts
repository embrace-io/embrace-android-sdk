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

// The TLS certificates the fake serves HTTPS with (FakeBackendTls), generated for each build rather than committed with
// their private key. They go into this module's resources, under fake-backend-tls/, for the fake wherever it runs, and
// out as fakeBackendTlsElements, for the benchmarked apps to trust the root. They're generated again only when this
// module's code or dependencies change, so the apps and the benchmarks built from one checkout agree on them.
val fakeBackendTlsDir = layout.buildDirectory.dir("generated/fakeBackendTls")
val generateFakeBackendTls = tasks.register<JavaExec>("generateFakeBackendTls") {
    val out = fakeBackendTlsDir
    classpath(sourceSets.main.map { it.output.classesDirs }, configurations.named("runtimeClasspath"))
    mainClass.set("io.embrace.android.testapiserver.FakeBackendTlsKt")
    outputs.dir(out)
    argumentProviders.add(CommandLineArgumentProvider { listOf(out.get().dir("fake-backend-tls").asFile.absolutePath) })
}
sourceSets.main { resources.srcDir(generateFakeBackendTls) }

configurations.consumable("fakeBackendTlsElements") {
    attributes { attribute(Category.CATEGORY_ATTRIBUTE, objects.named(Category::class.java, "fake-backend-tls")) }
    outgoing.artifact(fakeBackendTlsDir.map { it.dir("fake-backend-tls") }) { builtBy(generateFakeBackendTls) }
}

dependencies {
    api(platform(libs.okhttp.bom))
    api(libs.mockwebserver)
    api(libs.okhttp.tls)

    testImplementation(libs.junit)
}

import org.jetbrains.kotlin.gradle.dsl.JvmTarget

buildscript {
    // Only resolved when the flag is set, so a plain checkout needs no prior publish. See the
    // comment on `instrumentConfig` below.
    if (providers.gradleProperty("embrace.macrobenchmark.instrument").getOrElse("false").toBoolean()) {
        repositories {
            mavenLocal()
            google()
            mavenCentral()
        }
        dependencies {
            classpath("io.embrace:embrace-gradle-plugin:${providers.gradleProperty("version").get()}")
        }
    }
}

plugins {
    id("com.android.application")
}

/**
 * The Embrace gradle plugin is the only thing that can give the app a real appId: it rewrites
 * `InstrumentedConfigImpl`'s bytecode with the values from `src/main/embrace-config.json`. The
 * plugin is a subproject of this build, so it can only be applied from a published copy:
 *
 * ```
 * ./gradlew publishToMavenLocal -Psigning.skip
 * ./gradlew :embrace-macrobenchmark:connectedBenchmarkAndroidTest \
 *     -Pembrace.macrobenchmark.instrument=true
 * ```
 *
 * The flag defaults to false in gradle.properties so that a checkout which hasn't published the
 * plugin still configures and compiles - that is all CI needs from this module. A build without
 * it has no appId, and the SDK refuses to start without one, so it is only good for compiling.
 */
val instrumentConfig = providers.gradleProperty("embrace.macrobenchmark.instrument").getOrElse("false").toBoolean()

if (instrumentConfig) {
    apply(plugin = "io.embrace.gradle")

    // The plugin auto-adds io.embrace:embrace-android-sdk at its own version. Point that at the
    // working tree instead, so the benchmark measures this checkout rather than a published copy.
    configurations.configureEach {
        resolutionStrategy.dependencySubstitution {
            substitute(module("io.embrace:embrace-android-sdk"))
                .using(project(":embrace-android-sdk"))
        }
    }
}

android {
    namespace = "io.embrace.android.embracesdk.macrobenchmark.app"
    compileSdk = libs.versions.compileSdk.get().toInt()

    defaultConfig {
        applicationId = "io.embrace.android.embracesdk.macrobenchmark.app"
        minSdk = 26
        versionCode = 1
        versionName = "1.0"
    }

    buildTypes {
        create("benchmark") {
            initWith(getByName("release"))
            isMinifyEnabled = false
            isDebuggable = false
            signingConfig = signingConfigs.getByName("debug")
            matchingFallbacks += listOf("release")
        }
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
    implementation(project(":embrace-android-sdk"))

    // macrobenchmark needs 1.4.0+ to install a baseline profile on API 34+, overriding the
    // older version the SDK pins for its own build
    implementation(libs.profileinstaller.benchmark)
}

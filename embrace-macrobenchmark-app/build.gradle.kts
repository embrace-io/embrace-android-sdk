import org.jetbrains.kotlin.gradle.dsl.JvmTarget

buildscript {
    // Resolved only if a copy is already in mavenLocal, because a buildscript classpath is resolved
    // before any task can publish one. See the comment on `instrumentConfig` below.
    val pluginVersion = providers.gradleProperty("version").get()
    val localRepo = System.getProperty("maven.repo.local") ?: "${System.getProperty("user.home")}/.m2/repository"
    val published = file("$localRepo/io/embrace/embrace-gradle-plugin/$pluginVersion").isDirectory

    extra["embracePluginPublished"] = published

    if (published) {
        repositories {
            mavenLocal()
            google()
            mavenCentral()
        }
        dependencies {
            classpath("io.embrace:embrace-gradle-plugin:$pluginVersion")
        }
    }
}

plugins {
    id("com.android.application")
}

/**
 * The Embrace gradle plugin is the only thing that can give the app a real appId: it rewrites
 * `InstrumentedConfigImpl`'s bytecode with the values from `src/main/embrace-config.json`, and the
 * SDK refuses to start without one.
 *
 * The plugin is a subproject of this build, so it can only be applied from a published copy, and a
 * buildscript classpath is resolved long before `:embrace-gradle-plugin:publishToMavenLocal` could
 * run. `:embrace-macrobenchmark:connectedBenchmarkAndroidTest` therefore publishes it for the *next*
 * build, and a checkout that has never published simply configures without it - that is all CI needs
 * from this module, and SessionBenchmark reports `sdk-not-started` rather than measuring nothing.
 */
val instrumentConfig = extra["embracePluginPublished"] as Boolean

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

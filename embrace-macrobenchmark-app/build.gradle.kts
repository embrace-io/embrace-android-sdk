import org.jetbrains.kotlin.gradle.dsl.JvmTarget

buildscript {
    // Resolved only if a copy is already in the integration test repo, because a buildscript classpath
    // is resolved before any task can publish one. See the comment on `instrumentConfig` below.
    val pluginVersion = providers.gradleProperty("version").get()
    val repo = isolated.rootProject.projectDirectory.dir(INTEGRATION_TEST_REPO_PATH)
    val published = repo.dir("io/embrace/embrace-gradle-plugin/$pluginVersion").asFile.isDirectory

    extra["embracePluginPublished"] = published

    if (published) {
        repositories {
            maven { url = uri(repo) }
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
 * buildscript classpath is resolved long before
 * `:embrace-gradle-plugin:publishAllPublicationsToIntegrationTestRepository` could run.
 * `:embrace-macrobenchmark:connectedBenchmarkAndroidTest` therefore publishes it for the *next*
 * build, and a checkout that has never published simply configures without it - that is all CI needs
 * from this module, and SessionBenchmark reports `sdk-not-started` rather than measuring nothing.
 */
val instrumentConfig = extra["embracePluginPublished"] as Boolean

if (instrumentConfig) {
    apply(plugin = "io.embrace.gradle")

    // Stop the plugin adding io.embrace:embrace-android-sdk at its own version, so the benchmark measures
    // the working tree's SDK, which is already a project dependency. A dependency substitution would also
    // work, but it forces Gradle to resolve every configuration at configuration time to find task
    // dependencies. The extension class is only on the classpath when the plugin is, hence the dynamic call.
    @Suppress("UNCHECKED_CAST")
    (extensions.getByName("embrace").withGroovyBuilder { getProperty("autoAddEmbraceDependencies") } as Property<Boolean>)
        .set(false)
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
        create("baselineProfile") {
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

    // run on CI only
    lint {
        checkDependencies = false
        checkReleaseBuilds = false
        ignoreTestSources = true
        quiet = true
    }
}

val baselineProfile = providers.gradleProperty("embrace.baselineProfile").isPresent

androidComponents {
    beforeVariants(selector().all()) {
        it.enable = it.buildType == "benchmark" || (baselineProfile && it.buildType == "baselineProfile")
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_11)
    }
}

dependencies {
    implementation(project(":embrace-android-sdk"))
    implementation(project(":embrace-android-config"))
    implementation(project(":embrace-android-storage"))
    implementation(project(":embrace-benchmark-common"))
    implementation(project(":embrace-android-otel-java"))
    implementation(libs.opentelemetry.kotlin.api)
    implementation(platform(libs.opentelemetry.bom))
    implementation(libs.opentelemetry.api)

    implementation(libs.profileinstaller)
    implementation(libs.okio)

    testImplementation(libs.junit)
    testImplementation(project(":embrace-test-common"))
}

// Linting this app's variant runs lintAnalyzeRelease on every library, even with checkDependencies
// off, so keep lint out of `check` to keep local builds fast. CI runs it via the root `ciBuild` task.
tasks.named("check") {
    setDependsOn(dependsOn.filterNot { it is TaskProvider<*> && it.name == "lint" })
}

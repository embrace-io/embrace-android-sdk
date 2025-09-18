plugins {
    id("com.android.library")
    kotlin("android")
    id("io.embrace.internal.build-logic")
    id("com.google.devtools.ksp")
}

embrace {
    containsPublicApi.set(true)
}

description = "Embrace Android SDK: Core"

android {
    ndkVersion = "22.1.7171670"

    defaultConfig {
        namespace = "io.embrace.android.embracesdk"
        consumerProguardFiles("embrace-proguard.cfg")
    }

    externalNativeBuild {
        cmake {
            path = file("CMakeLists.txt")
        }
    }
    packaging {
        jniLibs.pickFirsts.add("**/*.so")
    }
}

kover {
    reports {
        filters {
            excludes {
                androidGeneratedClasses()
                classes("*.BuildConfig")
            }
        }
        variant("release") {
            xml {}
        }
    }
}

val codeCoverageModules = listOf( // FIXME: future: add gradle plugin to code coverage
    ":embrace-android-api",
    ":embrace-internal-api",
    ":embrace-android-sdk",
    ":embrace-android-core",
    ":embrace-android-infra",
    ":embrace-android-features",
    ":embrace-android-payload",
    ":embrace-android-delivery",
    ":embrace-android-okhttp3",
    ":embrace-android-fcm",
    ":embrace-android-compose",
    ":embrace-android-otel",
)
codeCoverageModules.forEach { projectName ->
    dependencies.add("kover", project(projectName))
}

dependencies {
    api(project(":embrace-android-api"))
    implementation(project(":embrace-android-infra"))
    implementation(project(":embrace-android-core"))
    implementation(project(":embrace-android-features"))
    implementation(project(":embrace-android-payload"))
    implementation(project(":embrace-android-delivery"))
    implementation(project(":embrace-internal-api"))
    implementation(project(":embrace-android-otel"))

    implementation(libs.opentelemetry.java.aliases)

    // lifecycle
    implementation(libs.lifecycle.runtime)
    implementation(libs.lifecycle.process)
    ksp(libs.lifecycle.compiler)
    testImplementation(libs.lifecycle.testing)

    // json
    implementation(libs.moshi)
    ksp(libs.moshi.kotlin.codegen)

    implementation(libs.opentelemetry.api)
    implementation(libs.opentelemetry.semconv)
    implementation(libs.opentelemetry.semconv.incubating)
    implementation(libs.profileinstaller)

    implementation(libs.opentelemetry.kotlin.api)
    implementation(libs.opentelemetry.kotlin.api.ext)
    implementation(libs.opentelemetry.kotlin.semconv)
    implementation(libs.opentelemetry.kotlin.noop)

    testImplementation(project(":embrace-test-fakes"))
    testImplementation(libs.protobuf.java)
    testImplementation(libs.protobuf.java.util)
    testImplementation(libs.kotlin.reflect)
    testImplementation(platform(libs.okhttp.bom))
    testImplementation(libs.mockwebserver)
    testImplementation(libs.opentelemetry.sdk)

    androidTestImplementation(project(":embrace-test-fakes"))

    testImplementation(project(":embrace-android-otel-java"))
    testImplementation(libs.opentelemetry.kotlin.compat)
    androidTestImplementation(project(":embrace-android-otel-java"))
    androidTestImplementation(libs.opentelemetry.kotlin.compat)
}

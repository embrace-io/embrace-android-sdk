plugins {
    id("com.android.library")
    kotlin("android")
    id("io.embrace.internal.build-logic")
    id("com.google.devtools.ksp")
    id("com.vanniktech.maven.publish")
}

description = "Embrace Android SDK: Core"

android {
    ndkVersion = "29.0.14206865"

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

dependencies {
    api(project(":embrace-android-api"))
    implementation(project(":embrace-android-infra"))
    implementation(project(":embrace-android-utils"))
    implementation(project(":embrace-android-core"))
    implementation(project(":embrace-android-features"))
    implementation(project(":embrace-android-payload"))
    implementation(project(":embrace-android-delivery"))
    implementation(project(":embrace-internal-api"))
    implementation(project(":embrace-android-otel"))
    implementation(project(":embrace-android-instrumentation-schema"))
    implementation(project(":embrace-android-instrumentation-api"))

    // automatically included instrumentation
    implementation(project(":embrace-android-instrumentation-power-save"))
    implementation(project(":embrace-android-instrumentation-taps"))
    implementation(project(":embrace-android-instrumentation-thermal-state"))
    implementation(project(":embrace-android-instrumentation-app-exit-info"))
    implementation(project(":embrace-android-instrumentation-fcm"))
    implementation(project(":embrace-android-instrumentation-webview"))

    implementation(libs.opentelemetry.java.aliases)

    // lifecycle
    implementation(libs.lifecycle.runtime)
    implementation(libs.lifecycle.process)
    ksp(libs.lifecycle.compiler)
    testImplementation(libs.lifecycle.testing)

    // json
    implementation(libs.moshi)
    ksp(libs.moshi.kotlin.codegen)

    implementation(libs.profileinstaller)

    implementation(libs.opentelemetry.kotlin.api)
    implementation(libs.opentelemetry.kotlin.api.ext)
    implementation(libs.opentelemetry.kotlin.semconv)
    implementation(libs.opentelemetry.kotlin.noop)

    androidTestImplementation(project(":embrace-test-fakes"))
    androidTestImplementation(project(":embrace-android-otel-java"))
    androidTestImplementation(libs.androidx.test.core)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestUtil(libs.androidx.test.orchestrator)

    testImplementation(project(":embrace-android-otel-java"))
    testImplementation(project(":embrace-test-fakes"))
    testImplementation(project(":embrace-test-common"))
    testImplementation(project(":embrace-android-config-fakes"))
    testImplementation(project(":embrace-android-delivery-fakes"))
    testImplementation(project(":embrace-android-instrumentation-api-fakes"))
    testImplementation(project(":embrace-android-otel-fakes"))
    testImplementation(project(":embrace-android-instrumentation-huc"))
    testImplementation(project(":embrace-android-instrumentation-huc-lite"))
    testImplementation(project(":embrace-android-instrumentation-power-save"))
    testImplementation(project(":embrace-android-instrumentation-thermal-state"))
    testImplementation(libs.protobuf.java)
    testImplementation(libs.protobuf.java.util)
    testImplementation(platform(libs.okhttp.bom))
    testImplementation(libs.mockwebserver)
    testImplementation(libs.mockk)
    testImplementation(libs.opentelemetry.sdk)
    testImplementation(libs.robolectric)
    testImplementation(libs.opentelemetry.kotlin.compat)
    androidTestImplementation(libs.opentelemetry.kotlin.compat)

    lintChecks(project(":embrace-lint"))
}

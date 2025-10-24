plugins {
    id("com.android.library")
    kotlin("android")
    id("io.embrace.internal.build-logic")
    id("com.vanniktech.maven.publish")
}

description = "Embrace Android SDK: Features"

android {
    namespace = "io.embrace.android.embracesdk.features"
}

dependencies {
    compileOnly(project(":embrace-android-api"))
    compileOnly(project(":embrace-android-core"))
    compileOnly(project(":embrace-android-infra"))
    compileOnly(project(":embrace-android-utils"))
    compileOnly(project(":embrace-android-payload"))
    compileOnly(project(":embrace-internal-api"))
    compileOnly(project(":embrace-android-otel"))
    implementation(project(":embrace-android-instrumentation-api"))
    implementation(project(":embrace-android-instrumentation-schema"))

    implementation(libs.lifecycle.process)
    implementation(libs.opentelemetry.kotlin.api)
    implementation(libs.opentelemetry.kotlin.api.ext)
    implementation(libs.opentelemetry.kotlin.semconv)

    testImplementation(project(":embrace-android-api"))
    testImplementation(project(":embrace-android-core"))
    testImplementation(project(":embrace-android-infra"))
    testImplementation(project(":embrace-android-utils"))
    testImplementation(project(":embrace-android-payload"))
    testImplementation(project(":embrace-internal-api"))
    testImplementation(project(":embrace-android-otel"))
    testImplementation(project(":embrace-test-fakes"))
    testImplementation(project(":embrace-test-common"))
    testImplementation(libs.robolectric)
    testImplementation(platform(libs.opentelemetry.bom))
    testImplementation(libs.opentelemetry.api)
    testImplementation(libs.opentelemetry.context)
    testImplementation(libs.protobuf.java)
    testImplementation(libs.protobuf.java.util)
    testImplementation(libs.mockk)
}

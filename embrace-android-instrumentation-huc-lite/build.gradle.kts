plugins {
    kotlin("android")
    id("com.android.library")
    id("io.embrace.internal.build-logic")
    id("com.vanniktech.maven.publish")
}

description = "Embrace Android SDK: HttpUrlConnection Lite instrumentation"

android {
    namespace = "io.embrace.android.embracesdk.instrumentation.huclite"
}

dependencies {
    implementation(project(":embrace-android-instrumentation-api"))
    implementation(libs.opentelemetry.kotlin.semconv)

    testImplementation(project(":embrace-android-instrumentation-api-fakes"))
    testImplementation(project(":embrace-android-config-fakes"))
    testImplementation(project(":embrace-test-fakes"))
    testImplementation(project(":embrace-test-common"))
    testImplementation(libs.robolectric)
    testImplementation(libs.mockk)
    implementation(libs.androidx.annotation)
}

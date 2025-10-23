plugins {
    id("com.android.library")
    kotlin("android")
    id("io.embrace.internal.build-logic")
    id("com.vanniktech.maven.publish")
}

description = "Embrace Android SDK: Taps Instrumentation"

android {
    namespace = "io.embrace.android.embracesdk.instrumentation.taps"
}

dependencies {
    implementation(project(":embrace-android-instrumentation-api"))
    implementation(libs.androidx.annotation)

    testImplementation(project(":embrace-test-fakes"))
}

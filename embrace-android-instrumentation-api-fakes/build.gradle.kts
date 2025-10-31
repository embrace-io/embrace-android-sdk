plugins {
    id("com.android.library")
    kotlin("android")
    id("io.embrace.internal.build-logic")
    id("com.vanniktech.maven.publish")
}

description = "Embrace Android SDK: Instrumentation API Fakes"

android {
    namespace = "io.embrace.android.embracesdk.internal.instrumentation.fakes"
}

dependencies {
    api(project(":embrace-android-instrumentation-api"))
    api(project(":embrace-android-config-fakes"))
    api(project(":embrace-test-common"))
}

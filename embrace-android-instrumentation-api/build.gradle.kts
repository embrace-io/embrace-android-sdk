plugins {
    id("com.android.library")
    kotlin("android")
    id("io.embrace.internal.build-logic")
    id("com.vanniktech.maven.publish")
}

description = "Embrace Android SDK: Instrumentation API"

android {
    namespace = "io.embrace.android.embracesdk.internal.instrumentation"
}

dependencies {
    api(project(":embrace-android-instrumentation-schema"))
    api(project(":embrace-android-infra"))
    api(project(":embrace-android-config"))

    testImplementation(project(":embrace-test-common"))
    testImplementation(project(":embrace-android-instrumentation-api-fakes"))
}

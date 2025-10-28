plugins {
    id("com.android.library")
    kotlin("android")
    id("io.embrace.internal.build-logic")
    id("com.vanniktech.maven.publish")
}

description = "Embrace Android SDK: Power Save Instrumentation"

android {
    namespace = "io.embrace.android.embracesdk.instrumentation.powersave"
}

dependencies {
    implementation(project(":embrace-android-instrumentation-api"))
    implementation(project(":embrace-android-utils"))

    testImplementation(project(":embrace-test-common"))
    testImplementation(project(":embrace-android-instrumentation-api-fakes"))
    testImplementation(libs.mockk)
    testImplementation(libs.robolectric)
}

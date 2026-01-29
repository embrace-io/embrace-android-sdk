plugins {
    id("embrace-prod-android-conventions")
}

description = "Embrace Android SDK: ANR Instrumentation"

android {
    namespace = "io.embrace.android.embracesdk.instrumentation.crash.anr"
}

dependencies {
    implementation(project(":embrace-android-instrumentation-api"))
    implementation(project(":embrace-android-instrumentation-profiler"))

    testImplementation(project(":embrace-android-instrumentation-api-fakes"))
    testImplementation(libs.mockk)
    testImplementation(libs.robolectric)
}

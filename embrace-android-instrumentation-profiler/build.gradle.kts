plugins {
    id("embrace-prod-android-conventions")
}

description = "Embrace Android SDK: Profiler"

android {
    namespace = "io.embrace.android.embracesdk.instrumentation.profiler"
}

dependencies {
    implementation(project(":embrace-android-instrumentation-api"))

    testImplementation(project(":embrace-android-instrumentation-api-fakes"))
    testImplementation(libs.mockk)
}

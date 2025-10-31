plugins {
    id("embrace-prod-android-conventions")
}

description = "Embrace Android SDK: View Instrumentation"

android {
    namespace = "io.embrace.android.embracesdk.instrumentation.view"
}

dependencies {
    implementation(project(":embrace-android-instrumentation-api"))

    testImplementation(project(":embrace-android-instrumentation-api-fakes"))
    testImplementation(libs.mockk)
}

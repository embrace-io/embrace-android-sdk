plugins {
    id("embrace-prod-android-conventions")
}

description = "Embrace Android SDK: Navigation State Instrumentation"

android {
    namespace = "io.embrace.android.embracesdk.instrumentation.navigation"
}

dependencies {
    implementation(project(":embrace-android-instrumentation-api"))
    testImplementation(project(":embrace-android-instrumentation-api-fakes"))
    testImplementation(libs.robolectric)
}

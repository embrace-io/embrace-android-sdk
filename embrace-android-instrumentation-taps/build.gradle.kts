plugins {
    id("embrace-prod-android-conventions")
}

description = "Embrace Android SDK: Taps Instrumentation"

android {
    namespace = "io.embrace.android.embracesdk.instrumentation.taps"
}

dependencies {
    implementation(project(":embrace-android-instrumentation-api"))

    testImplementation(project(":embrace-android-instrumentation-api-fakes"))
    testImplementation(libs.robolectric)
}

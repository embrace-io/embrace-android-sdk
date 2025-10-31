plugins {
    id("embrace-prod-android-conventions")
}

description = "Embrace Android SDK: Power Save Instrumentation"

android {
    namespace = "io.embrace.android.embracesdk.instrumentation.powersave"
}

dependencies {
    implementation(project(":embrace-android-instrumentation-api"))
    implementation(project(":embrace-android-utils"))

    testImplementation(project(":embrace-android-instrumentation-api-fakes"))
    testImplementation(libs.robolectric)
    testImplementation(libs.mockk)
}

plugins {
    id("embrace-prod-android-conventions")
}

description = "Embrace Android SDK: Thermal State Instrumentation"

android {
    namespace = "io.embrace.android.embracesdk.instrumentation.thermal"
}

dependencies {
    implementation(project(":embrace-android-instrumentation-api"))
    implementation(project(":embrace-android-utils"))
    implementation(libs.androidx.annotation)

    testImplementation(project(":embrace-android-instrumentation-api-fakes"))
    testImplementation(libs.mockk)
    testImplementation(libs.robolectric)
}

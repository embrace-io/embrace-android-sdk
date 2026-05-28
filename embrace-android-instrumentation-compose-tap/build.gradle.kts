plugins {
    id("embrace-prod-android-conventions")
}

description = "Embrace Android SDK: Jetpack Compose"

android {
    namespace = "io.embrace.android.embracesdk.compose"
}

dependencies {
    implementation(project(":embrace-android-instrumentation-api"))
    implementation(project(":embrace-android-instrumentation-taps"))
    compileOnly(libs.compose)

    testImplementation(project(":embrace-android-instrumentation-api-fakes"))
    testImplementation(libs.compose)
    testImplementation(libs.robolectric)
}

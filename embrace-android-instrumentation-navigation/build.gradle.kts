plugins {
    id("embrace-prod-android-conventions")
}

description = "Embrace Android SDK: Navigation State Instrumentation"

android {
    namespace = "io.embrace.android.embracesdk.instrumentation.navigation"
}

dependencies {
    implementation(project(":embrace-android-instrumentation-api"))
    compileOnly(libs.androidx.navigation.fragment)
    compileOnly(libs.androidx.navigation.common)
    testImplementation(project(":embrace-android-instrumentation-api-fakes"))
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.navigation.fragment)
    testImplementation(libs.androidx.navigation.common)
}

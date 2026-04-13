plugins {
    id("embrace-prod-android-conventions")
}

description = "Embrace Android SDK: Compose Navigation Support"

android {
    namespace = "io.embrace.android.embracesdk.instrumentation.compose.navigation"
}

dependencies {
    implementation(project(":embrace-android-instrumentation-api"))
    implementation(project(":embrace-android-instrumentation-navigation"))
    implementation(libs.androidx.navigation.fragment)
    implementation(libs.androidx.navigation.common)
    testImplementation(project(":embrace-android-instrumentation-api-fakes"))
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.navigation.testing)
}

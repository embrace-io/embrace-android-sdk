plugins {
    id("embrace-prod-android-conventions")
}

description = "Embrace Android SDK: WebView Instrumentation"

android {
    namespace = "io.embrace.android.embracesdk.instrumentation.webview"
}

dependencies {
    implementation(project(":embrace-android-instrumentation-api"))

    testImplementation(project(":embrace-android-instrumentation-api-fakes"))
    testImplementation(project(":embrace-test-common"))
    testImplementation(libs.mockk)
    testImplementation(libs.robolectric)
}

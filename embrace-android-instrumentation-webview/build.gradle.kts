plugins {
    id("embrace-prod-android-conventions")
}

description = "Embrace Android SDK: WebView Instrumentation"

android {
    namespace = "io.embrace.android.embracesdk.instrumentation.webview"
}

dependencies {
    implementation(project(":embrace-android-instrumentation-api"))
    implementation(libs.androidx.annotation)

    testImplementation(project(":embrace-android-instrumentation-api-fakes"))
    testImplementation(libs.opentelemetry.kotlin.semconv)
    testImplementation(libs.mockk)
}

plugins {
    id("embrace-prod-android-conventions")
}

description = "Embrace Android SDK: Network Status Instrumentation"

android {
    namespace = "io.embrace.android.embracesdk.instrumentation.network.status"
}

dependencies {
    implementation(project(":embrace-android-instrumentation-api"))
    testImplementation(project(":embrace-android-instrumentation-api-fakes"))
}

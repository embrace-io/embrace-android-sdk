plugins {
    id("embrace-prod-android-conventions")
}

description = "Embrace Android SDK: Startup Trace Instrumentation"

android {
    namespace = "io.embrace.android.embracesdk.instrumentation.startup"
}

dependencies {
    implementation(project(":embrace-android-api"))
    implementation(project(":embrace-android-instrumentation-api"))

    testImplementation(project(":embrace-android-instrumentation-api-fakes"))
    testImplementation(project(":embrace-test-common"))
    testImplementation(libs.robolectric)
}

plugins {
    id("embrace-prod-android-conventions")
}

description = "Embrace Android SDK: Common Network Instrumentation"

android {
    namespace = "io.embrace.android.embracesdk.internal.instrumentation.network.common"
}

dependencies {
    implementation(project(":embrace-android-instrumentation-api"))

    testImplementation(project(":embrace-android-instrumentation-api-fakes"))
    testImplementation(project(":embrace-test-common"))
    testImplementation(libs.robolectric)
}

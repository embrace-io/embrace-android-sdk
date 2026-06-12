plugins {
    id("embrace-prod-android-conventions")
}

description = "Embrace Android SDK: Mobile Vitals"

android {
    namespace = "io.embrace.android.embracesdk.instrumentation.vitals"
}

dependencies {
    implementation(libs.androidx.annotation)
    implementation(project(":embrace-android-instrumentation-api"))

    testImplementation(project(":embrace-android-instrumentation-api-fakes"))
    testImplementation(libs.robolectric)
    testImplementation(project(":embrace-test-common"))
}

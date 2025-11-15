plugins {
    id("embrace-prod-android-conventions")
}

description = "Embrace Android SDK: Startup Instrumentation"

android {
    namespace = "io.embrace.android.embracesdk.instrumentation.startup"
}

dependencies {
    // required for accessing startup annotations
    implementation(project(":embrace-android-api"))
    implementation(project(":embrace-android-instrumentation-api"))
    implementation(project(":embrace-android-utils"))
    implementation(libs.lifecycle.process)
    implementation(libs.androidx.annotation)

    testImplementation(project(":embrace-android-instrumentation-api-fakes"))
    testImplementation(libs.robolectric)
}

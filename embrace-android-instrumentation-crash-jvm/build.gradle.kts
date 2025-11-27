plugins {
    id("embrace-prod-android-conventions")
}

description = "Embrace Android SDK: JVM Crash Instrumentation"

android {
    namespace = "io.embrace.android.embracesdk.instrumentation.crash.jvm"
}

dependencies {
    implementation(project(":embrace-android-instrumentation-api"))

    testImplementation(project(":embrace-android-instrumentation-api-fakes"))
    testImplementation(libs.robolectric)
}

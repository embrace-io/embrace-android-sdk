
plugins {
    id("com.android.library")
    kotlin("android")
    id("io.embrace.internal.build-logic")
    id("com.vanniktech.maven.publish")
}


description = "Embrace Android SDK: Network Status Instrumentation"

android {
    namespace = "io.embrace.android.embracesdk.instrumentation.network.status"
}

dependencies {
    implementation(project(":embrace-android-instrumentation-api"))

    testImplementation(project(":embrace-test-common"))
    testImplementation(project(":embrace-android-instrumentation-api-fakes"))
}

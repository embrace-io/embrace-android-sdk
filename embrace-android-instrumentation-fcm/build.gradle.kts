plugins {
    id("com.android.library")
    kotlin("android")
    id("io.embrace.internal.build-logic")
    id("com.vanniktech.maven.publish")
}

description = "Embrace Android SDK: Firebase Cloud Messaging"

android {
    namespace = "io.embrace.android.embracesdk.fcm"
}

dependencies {
    implementation(project(":embrace-android-instrumentation-api"))
    compileOnly(libs.firebase.messaging)

    testImplementation(project(":embrace-test-common"))
    testImplementation(libs.mockk)
}

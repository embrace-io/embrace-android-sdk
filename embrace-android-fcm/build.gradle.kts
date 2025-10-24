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
    compileOnly(libs.firebase.messaging)
    compileOnly(project(":embrace-android-sdk"))
    compileOnly(project(":embrace-internal-api"))
}

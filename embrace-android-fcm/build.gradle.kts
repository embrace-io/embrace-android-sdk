plugins {
    id("io.embrace.internal.build-logic")
}

description = "Embrace Android SDK: Firebase Cloud Messaging"

android {
    namespace = "io.embrace.android.embracesdk.fcm"
}

dependencies {
    compileOnly(libs.firebase.messaging)
    compileOnly(project(":embrace-android-sdk"))
    compileOnly(project(":embrace-internal-api"))
    testImplementation(project(":embrace-android-sdk"))
}

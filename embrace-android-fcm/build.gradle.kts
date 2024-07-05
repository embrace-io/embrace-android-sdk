plugins {
    id("embrace-defaults")
}

description = "Embrace Android SDK: Firebase Cloud Messaging"

android {
    namespace = "io.embrace.android.embracesdk.fcm"
}

dependencies {
    compileOnly(libs.firebase.messaging)
    compileOnly(project(":embrace-android-api"))
    compileOnly(project(":embrace-android-sdk"))
    testImplementation(project(":embrace-android-api"))
    testImplementation(project(":embrace-android-sdk"))
}

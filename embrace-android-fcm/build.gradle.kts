plugins {
    id("internal-embrace-plugin")
}

description = "Embrace Android SDK: Firebase Cloud Messaging"

android {
    namespace = "io.embrace.android.embracesdk.fcm"
}

dependencies {
    compileOnly("com.google.firebase:firebase-messaging:23.1.0")
    compileOnly(project(":embrace-android-sdk"))
}

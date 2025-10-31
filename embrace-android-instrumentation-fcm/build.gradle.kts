plugins {
    id("embrace-prod-android-conventions")
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

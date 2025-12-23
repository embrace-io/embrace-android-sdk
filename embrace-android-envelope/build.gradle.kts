plugins {
    id("embrace-prod-android-conventions")
}

description = "Embrace Android SDK: Envelope"

android {
    namespace = "io.embrace.android.embracesdk.envelope"
}

dependencies {
    implementation(project(":embrace-android-payload"))
}

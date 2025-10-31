plugins {
    id("embrace-prod-android-conventions")
}

description = "Embrace Android SDK: Internal API"

android {
    namespace = "io.embrace.android.embracesdk.api.internal"
}

dependencies {
    compileOnly(project(":embrace-android-api"))
    testImplementation(project(":embrace-android-api"))
    testImplementation(libs.mockk)
}

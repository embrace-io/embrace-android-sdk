plugins {
    id("embrace-defaults")
}

description = "Embrace Android SDK: OkHttp3"

android {
    namespace = "io.embrace.android.embracesdk.okhttp3"
}

dependencies {
    compileOnly(libs.okhttp)
    compileOnly(project(":embrace-android-api"))
    compileOnly(project(":embrace-android-sdk"))
    testImplementation(project(":embrace-android-api"))
    testImplementation(project(":embrace-android-sdk"))
}

plugins {
    id("internal-embrace-plugin")
}

description = "Embrace Android SDK: OkHttp3"

android {
    namespace = "io.embrace.android.embracesdk.okhttp3"
}

dependencies {
    compileOnly("com.squareup.okhttp3:okhttp:4.9.3")
    compileOnly(project(":embrace-android-sdk"))
    testImplementation(project(":embrace-android-sdk"))
    testImplementation("io.mockk:mockk:1.12.2")
    testImplementation("com.squareup.okhttp3:mockwebserver:4.9.3")
}

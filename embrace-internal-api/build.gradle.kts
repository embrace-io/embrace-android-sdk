plugins {
    id("com.android.library")
    kotlin("android")
    id("io.embrace.internal.build-logic")
    id("com.vanniktech.maven.publish")
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

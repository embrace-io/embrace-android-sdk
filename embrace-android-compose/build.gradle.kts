plugins {
    id("com.android.library")
    kotlin("android")
    id("io.embrace.internal.build-logic")
    id("com.vanniktech.maven.publish")
}

description = "Embrace Android SDK: Jetpack Compose"

android {
    namespace = "io.embrace.android.embracesdk.compose"
}

dependencies {
    implementation(libs.lifecycle.process)
    compileOnly(libs.compose)
    compileOnly(project(":embrace-internal-api"))
    compileOnly(project(":embrace-android-instrumentation-api"))
    compileOnly(project(":embrace-android-instrumentation-taps"))
}

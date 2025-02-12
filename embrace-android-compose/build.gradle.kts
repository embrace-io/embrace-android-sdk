plugins {
    id("com.android.library")
    id("kotlin-android")
    id("io.embrace.internal.build-logic")
}

description = "Embrace Android SDK: Jetpack Compose"

android {
    namespace = "io.embrace.android.embracesdk.compose"
}

dependencies {
    implementation(libs.lifecycle.runtime)
    implementation(libs.lifecycle.process)
    compileOnly(libs.compose)
    compileOnly(project(":embrace-internal-api"))
    compileOnly(project(":embrace-android-sdk"))
    testImplementation(project(":embrace-android-sdk"))
}

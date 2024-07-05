plugins {
    id("embrace-defaults")
}

description = "Embrace Android SDK: Jetpack Compose"

android {
    namespace = "io.embrace.android.embracesdk.compose"
}

dependencies {
    implementation(libs.lifecycle.common.java8)
    implementation(libs.lifecycle.extensions)
    compileOnly(libs.compose)
    compileOnly(project(":embrace-android-api"))
    compileOnly(project(":embrace-android-sdk"))
    testImplementation(project(":embrace-android-api"))
    testImplementation(project(":embrace-android-sdk"))
}
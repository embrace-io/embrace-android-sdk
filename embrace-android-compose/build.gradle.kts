plugins {
    id("embrace-prod-defaults")
}

description = "Embrace Android SDK: Jetpack Compose"

android {
    namespace = "io.embrace.android.embracesdk.compose"
}

dependencies {
    implementation(libs.lifecycle.runtime)
    implementation(libs.lifecycle.process)
    compileOnly(libs.compose)
    compileOnly(project(":embrace-android-sdk"))
    testImplementation(project(":embrace-android-sdk"))
}

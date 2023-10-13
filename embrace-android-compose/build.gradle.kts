plugins {
    id("internal-embrace-plugin")
}

description = "Embrace Android SDK: Jetpack Compose"

android {
    namespace = "io.embrace.android.embracesdk.compose"
}

dependencies {
    implementation("androidx.lifecycle:lifecycle-common-java8:2.5.0")
    implementation("androidx.lifecycle:lifecycle-extensions:2.0.0")
    //compose
    compileOnly("androidx.compose.ui:ui:1.0.5")
    compileOnly(project(":embrace-android-sdk"))
}
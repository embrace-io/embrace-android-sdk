plugins {
    kotlin("android")
    id("com.android.library")
    id("io.embrace.internal.build-logic")
    id("com.vanniktech.maven.publish")
}

description = "Embrace Android SDK: HttpUrlConnection Lite instrumentation"

android {
    namespace = "io.embrace.android.embracesdk.instrumentation.huclite"
}

dependencies {
    compileOnly(project(":embrace-android-core"))
    compileOnly(project(":embrace-android-sdk"))
    compileOnly(project(":embrace-internal-api"))
    testImplementation(project(":embrace-android-core"))
    testImplementation(project(":embrace-android-sdk"))
    testImplementation(project(":embrace-internal-api"))
    testImplementation(project(":embrace-test-fakes"))
    testImplementation(libs.robolectric)
    implementation(libs.androidx.annotation)
}

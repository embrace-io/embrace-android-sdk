plugins {
    kotlin("android")
    id("com.android.library")
    id("io.embrace.internal.build-logic")
}

description = "Embrace Android SDK: HttpUrlConnection instrumentation"

android {
    namespace = "io.embrace.android.embracesdk.instrumentation.huc"
}

dependencies {
    compileOnly(project(":embrace-android-core"))
    compileOnly(project(":embrace-android-sdk"))
    compileOnly(project(":embrace-internal-api"))
    testImplementation(project(":embrace-android-core"))
    testImplementation(project(":embrace-android-sdk"))
    testImplementation(project(":embrace-internal-api"))
    implementation(libs.androidx.annotation)
}

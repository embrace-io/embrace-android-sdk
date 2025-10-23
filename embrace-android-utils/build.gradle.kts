plugins {
    id("com.android.library")
    kotlin("android")
    id("io.embrace.internal.build-logic")
    id("com.vanniktech.maven.publish")
}

description = "Embrace Android SDK: Utils"

android {
    namespace = "io.embrace.android.embracesdk.utils"
}

dependencies {
    implementation(libs.androidx.annotation)
    implementation(project(":embrace-android-infra"))

    testImplementation(libs.robolectric)
}

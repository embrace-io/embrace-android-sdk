plugins {
    id("com.android.library")
    kotlin("android")
    id("io.embrace.internal.build-logic")
}

description = "Embrace Android SDK: Utils"

android {
    namespace = "io.embrace.android.embracesdk.utils"
}

dependencies {
    implementation(libs.androidx.annotation)
    implementation(project(":embrace-android-infra"))
}

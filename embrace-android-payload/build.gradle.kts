plugins {
    id("com.android.library")
    kotlin("android")
    id("io.embrace.internal.build-logic")
    id("com.google.devtools.ksp")
}

description = "Embrace Android SDK: Payload"

android {
    namespace = "io.embrace.android.embracesdk.payload"
}

dependencies {
    implementation(libs.moshi)
    ksp(libs.moshi.kotlin.codegen)
}

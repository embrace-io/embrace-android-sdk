plugins {
    id("com.android.library")
    kotlin("android")
    id("io.embrace.internal.build-logic")
    id("com.vanniktech.maven.publish")
}

description = "Embrace Android SDK: API"

android {
    namespace = "io.embrace.android.embracesdk.api"
}

dependencies {
    compileOnly(libs.opentelemetry.kotlin.api)
    implementation(libs.androidx.annotation)
    lintChecks(project(":embrace-lint"))
}

plugins {
    id("com.android.library")
    kotlin("android")
    id("io.embrace.internal.build-logic")
}

description = "Embrace Android SDK: OpenTelemetry Java API"

android {
    namespace = "io.embrace.android.embracesdk.otel.java"
}

dependencies {
    compileOnly(project(":embrace-android-sdk"))
    compileOnly(project(":embrace-internal-api"))
    compileOnly(platform(libs.opentelemetry.bom))
    compileOnly(libs.opentelemetry.api)
}

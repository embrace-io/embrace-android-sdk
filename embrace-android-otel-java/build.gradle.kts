plugins {
    id("embrace-prod-android-conventions")
}

description = "Embrace Android SDK: OpenTelemetry Java API"

android {
    namespace = "io.embrace.android.embracesdk.otel.java"
}

dependencies {
    compileOnly(project(":embrace-android-api"))
    compileOnly(libs.opentelemetry.sdk)

    implementation(libs.opentelemetry.java.aliases)
    implementation(libs.opentelemetry.kotlin.compat)
}

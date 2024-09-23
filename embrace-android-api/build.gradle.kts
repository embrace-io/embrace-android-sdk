plugins {
    id("embrace-prod-defaults")
    id("enable-explicit-api-mode")
}

description = "Embrace Android SDK: API"

android {
    namespace = "io.embrace.android.embracesdk.api"
}

dependencies {
    compileOnly(platform(libs.opentelemetry.bom))
    compileOnly(libs.opentelemetry.api)
    compileOnly(libs.opentelemetry.sdk)
    implementation(libs.lifecycle.process)
}

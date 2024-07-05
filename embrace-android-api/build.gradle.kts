plugins {
    id("embrace-defaults")
}

description = "Embrace Android SDK: API"

android {
    namespace = "io.embrace.android.embracesdk.api"
}

dependencies {
    implementation(libs.androidx.annotation)
    compileOnly(platform(libs.opentelemetry.bom))
    compileOnly(libs.opentelemetry.api)
    compileOnly(libs.opentelemetry.sdk)
    compileOnly(libs.opentelemetry.context)
}
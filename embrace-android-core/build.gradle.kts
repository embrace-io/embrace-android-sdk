plugins {
    id("embrace-defaults")
}

description = "Embrace Android SDK: Core"

android {
    namespace = "io.embrace.android.embracesdk.core"
}

dependencies {
    compileOnly(platform(libs.opentelemetry.bom))
    compileOnly(libs.opentelemetry.api)
    compileOnly(libs.opentelemetry.sdk)
    compileOnly(libs.opentelemetry.context)
    compileOnly(libs.opentelemetry.semconv)
    compileOnly(libs.opentelemetry.semconv.incubating)
}

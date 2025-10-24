plugins {
    id("com.android.library")
    kotlin("android")
    id("io.embrace.internal.build-logic")
}

description = "Embrace Android SDK: OTel fakes"

android {
    namespace = "io.embrace.android.embracesdk.otel.fakes"
}

dependencies {
    implementation(project(":embrace-android-otel"))
    implementation(project(":embrace-android-infra"))
    implementation(project(":embrace-android-utils"))
    implementation(project(":embrace-android-payload"))
    implementation(project(":embrace-android-instrumentation-schema"))
    implementation(project(":embrace-android-api"))
    implementation(project(":embrace-test-common"))

    implementation(libs.opentelemetry.kotlin.api)
    implementation(libs.opentelemetry.kotlin.sdk)
    implementation(libs.opentelemetry.kotlin.compat)
    implementation(libs.opentelemetry.kotlin.semconv)
    implementation(libs.opentelemetry.java.aliases)

    implementation(platform(libs.opentelemetry.bom))
    implementation(libs.opentelemetry.api)
    implementation(libs.opentelemetry.sdk)
    implementation(libs.junit)
}

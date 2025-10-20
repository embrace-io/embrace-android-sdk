plugins {
    id("com.android.library")
    kotlin("android")
    id("io.embrace.internal.build-logic")
}

embrace {
}

description = "Embrace Android SDK: OTel"

android {
    namespace = "io.embrace.android.embracesdk.otel"
}

dependencies {
    implementation(project(":embrace-android-infra"))
    implementation(project(":embrace-android-utils"))
    implementation(project(":embrace-android-payload"))
    implementation(project(":embrace-android-instrumentation-schema"))
    compileOnly(project(":embrace-android-api"))
    testImplementation(project(":embrace-android-payload"))
    testImplementation(project(":embrace-android-api"))


    testImplementation(platform(libs.opentelemetry.bom))
    testImplementation(libs.opentelemetry.api)
    testImplementation(libs.opentelemetry.sdk)

    implementation(libs.opentelemetry.kotlin.api)
    implementation(libs.opentelemetry.kotlin.api.ext)
    implementation(libs.opentelemetry.kotlin.sdk)
    implementation(libs.opentelemetry.kotlin.compat)
    implementation(libs.opentelemetry.kotlin.semconv)
    implementation(libs.opentelemetry.java.aliases)
}

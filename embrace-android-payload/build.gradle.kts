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

    implementation(project(":embrace-android-api"))
    testImplementation(project(":embrace-android-api"))
    testImplementation(project(":embrace-android-core"))
    testImplementation(project(":embrace-android-payload"))
    testImplementation(platform(libs.opentelemetry.bom))
    testImplementation(libs.opentelemetry.api)
    testImplementation(libs.opentelemetry.sdk)
    testImplementation(libs.opentelemetry.semconv)
    testImplementation(libs.opentelemetry.semconv.incubating)
}

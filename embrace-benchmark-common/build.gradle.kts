plugins {
    id("embrace-common-conventions")
    id("embrace-android-conventions")
}

description = "Embrace Android SDK: Benchmark Common"

android {
    namespace = "io.embrace.android.embracesdk.benchmark"
}

dependencies {
    api(project(":embrace-android-api"))
    testImplementation(libs.junit)
    testImplementation(libs.mockk)
    testImplementation(libs.opentelemetry.kotlin.api)
    testImplementation(libs.opentelemetry.kotlin.sdk.api)
}

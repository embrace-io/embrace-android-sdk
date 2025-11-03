plugins {
    id("embrace-prod-android-conventions")
}

description = "Embrace Android SDK: JVM Crash Instrumentation"

android {
    namespace = "io.embrace.android.embracesdk.instrumentation.crash.jvm"
}

dependencies {
    implementation(project(":embrace-android-instrumentation-api"))
    implementation(libs.opentelemetry.kotlin.semconv)
    testImplementation(libs.robolectric)
}

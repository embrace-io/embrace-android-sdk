plugins {
    id("embrace-prod-android-conventions")
}

description = "Embrace Android SDK: Features"

android {
    namespace = "io.embrace.android.embracesdk.features"
}

dependencies {
    compileOnly(project(":embrace-android-core"))
    implementation(project(":embrace-android-instrumentation-api"))
    implementation(libs.opentelemetry.kotlin.api)
    implementation(libs.opentelemetry.kotlin.semconv)

    testImplementation(project(":embrace-android-core"))
    testImplementation(project(":embrace-test-fakes"))
    testImplementation(project(":embrace-android-instrumentation-api-fakes"))
    testImplementation(libs.robolectric)
}

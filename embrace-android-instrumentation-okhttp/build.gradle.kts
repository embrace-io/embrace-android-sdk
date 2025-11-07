plugins {
    id("embrace-prod-android-conventions")
}

description = "Embrace Android SDK: OkHttp3"

android {
    namespace = "io.embrace.android.embracesdk.okhttp3"
}

dependencies {
    compileOnly(platform(libs.okhttp.bom))
    compileOnly(libs.okhttp)
    implementation(project(":embrace-android-instrumentation-api"))
    implementation(project(":embrace-android-instrumentation-network-common"))
    implementation(libs.androidx.annotation)

    testImplementation(project(":embrace-android-instrumentation-api-fakes"))
    testImplementation(project(":embrace-test-common"))
    testImplementation(libs.opentelemetry.kotlin.semconv)
    testImplementation(libs.mockwebserver)
    testImplementation(libs.robolectric)
}

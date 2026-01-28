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

    testImplementation(project(":embrace-android-instrumentation-api-fakes"))
    testImplementation(project(":embrace-test-common"))
    testImplementation(platform(libs.okhttp.bom))
    testImplementation(libs.mockwebserver)
    testImplementation(libs.robolectric)
}

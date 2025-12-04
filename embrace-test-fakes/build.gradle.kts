plugins {
    id("embrace-android-conventions")
}

android {
    namespace = "io.embrace.android.embracesdk.test.fakes"
    packaging.resources.excludes += "META-INF/LICENSE.md"
    packaging.resources.excludes += "META-INF/LICENSE-notice.md"
}

dependencies {
    implementation(project(":embrace-android-api"))
    implementation(project(":embrace-test-common"))
    implementation(project(":embrace-android-config-fakes"))
    implementation(project(":embrace-android-otel-fakes"))
    implementation(project(":embrace-android-instrumentation-api-fakes"))
    implementation(project(":embrace-android-instrumentation-crash-ndk"))

    compileOnly(project(":embrace-android-core"))
    compileOnly(project(":embrace-android-infra"))
    compileOnly(project(":embrace-android-utils"))
    compileOnly(project(":embrace-android-payload"))
    compileOnly(project(":embrace-android-delivery"))
    compileOnly(project(":embrace-internal-api"))
    compileOnly(project(":embrace-android-otel"))
    compileOnly(project(":embrace-android-instrumentation-schema"))
    compileOnly(project(":embrace-android-instrumentation-api"))

    implementation(platform(libs.okhttp.bom))
    implementation(libs.okhttp)
    implementation(libs.opentelemetry.kotlin.api)

    implementation(libs.robolectric)
    implementation(libs.mockk)
}

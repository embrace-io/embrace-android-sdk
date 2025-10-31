plugins {
    id("embrace-android-conventions")
}

android {
    namespace = "io.embrace.android.embracesdk.test.fakes"
}

dependencies {
    implementation(project(":embrace-test-common"))
    implementation(project(":embrace-android-config-fakes"))
    implementation(project(":embrace-android-delivery-fakes"))
    implementation(project(":embrace-android-otel-fakes"))
    implementation(project(":embrace-android-instrumentation-api-fakes"))

    compileOnly(project(":embrace-android-core"))
    compileOnly(project(":embrace-android-infra"))
    compileOnly(project(":embrace-android-utils"))
    compileOnly(project(":embrace-android-sdk"))
    compileOnly(project(":embrace-android-payload"))
    compileOnly(project(":embrace-android-features"))
    compileOnly(project(":embrace-android-delivery"))
    compileOnly(project(":embrace-internal-api"))
    compileOnly(project(":embrace-android-otel"))
    compileOnly(project(":embrace-android-instrumentation-schema"))
    compileOnly(project(":embrace-android-instrumentation-api"))

    compileOnly(platform(libs.opentelemetry.bom))
    compileOnly(libs.opentelemetry.api)
    compileOnly(libs.opentelemetry.sdk)
    implementation(libs.opentelemetry.java.aliases)

    implementation(libs.junit)
    implementation(platform(libs.okhttp.bom))
    implementation(libs.okhttp)
    implementation(libs.lifecycle.runtime)

    implementation(libs.opentelemetry.kotlin.api)
    implementation(libs.opentelemetry.kotlin.api.ext)
    implementation(libs.opentelemetry.kotlin.sdk)
    implementation(libs.opentelemetry.kotlin.compat)
    implementation(libs.opentelemetry.kotlin.semconv)
}

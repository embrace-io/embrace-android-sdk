import io.embrace.gradle.Versions

plugins {
    id("embrace-test-defaults")
}

android {
    namespace = "io.embrace.android.embracesdk.test.fakes"
}

dependencies {
    compileOnly(project(":embrace-android-core"))
    compileOnly(project(":embrace-android-infra"))
    compileOnly(project(":embrace-android-sdk"))
    compileOnly(project(":embrace-android-payload"))
    compileOnly(project(":embrace-android-features"))
    compileOnly(project(":embrace-android-delivery"))

    compileOnly(platform(libs.opentelemetry.bom))
    compileOnly(libs.opentelemetry.api)
    compileOnly(libs.opentelemetry.sdk)
    compileOnly(libs.opentelemetry.semconv)
    compileOnly(libs.opentelemetry.semconv.incubating)

    implementation(libs.junit)
    implementation(libs.robolectric)
    implementation(libs.lifecycle.common.java8)
    implementation(libs.lifecycle.process)
}

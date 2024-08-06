import io.embrace.gradle.Versions

plugins {
    id("embrace-test-defaults")
}

android {
    namespace = "io.embrace.android.embracesdk.test.fakes"
}

dependencies {
    compileOnly(project(":embrace-android-core"))
    compileOnly(project(":embrace-android-sdk"))
    compileOnly(project(":embrace-android-payload"))
    compileOnly(project(":embrace-android-features"))

    compileOnly(platform(libs.opentelemetry.bom))
    compileOnly(libs.opentelemetry.api)
    compileOnly(libs.opentelemetry.sdk)

    implementation(libs.junit)
    implementation("org.robolectric:robolectric:${Versions.ROBOLECTRIC}")
    implementation(libs.lifecycle.common.java8)
    implementation(libs.lifecycle.process)
}

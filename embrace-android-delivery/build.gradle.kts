plugins {
    id("com.android.library")
    kotlin("android")
    id("io.embrace.internal.build-logic")
    id("com.vanniktech.maven.publish")
}

description = "Embrace Android SDK: Delivery"

android {
    namespace = "io.embrace.android.embracesdk.delivery"
}

dependencies {
    implementation(platform(libs.okhttp.bom))
    implementation(libs.okhttp)
    implementation(project(":embrace-android-payload"))
    implementation(project(":embrace-android-infra"))

    testImplementation(project(":embrace-test-fakes"))
    testImplementation(libs.mockwebserver)
    testImplementation(libs.mockk)
}

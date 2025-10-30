plugins {
    id("embrace-prod-android-conventions")
}

description = "Embrace Android SDK: Envelope"

android {
    namespace = "io.embrace.android.embracesdk.envelope"
}

dependencies {
    implementation(project(":embrace-android-payload"))
    implementation(project(":embrace-android-infra"))
    implementation(project(":embrace-android-utils"))

    testImplementation(project(":embrace-android-instrumentation-api-fakes"))
    testImplementation(project(":embrace-android-config-fakes"))
    testImplementation(libs.mockk)
}

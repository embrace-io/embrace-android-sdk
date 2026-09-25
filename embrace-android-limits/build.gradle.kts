plugins {
    id("embrace-prod-jvm-conventions")
}

description = "Embrace Android SDK: Telemetry Limits"

dependencies {
    implementation(project(":embrace-android-payload"))
    implementation(project(":embrace-android-infra"))

    testImplementation(project(":embrace-test-common"))
}

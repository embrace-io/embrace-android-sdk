plugins {
    id("embrace-prod-jvm-conventions")
}

description = "Embrace Android SDK: Storage"

dependencies {
    api(project(":embrace-android-infra"))
    api(libs.okio)

    testImplementation(project(":embrace-test-common"))
}

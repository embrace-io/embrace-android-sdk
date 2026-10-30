plugins {
    id("embrace-prod-jvm-conventions")
}

description = "Embrace Android SDK: Infra"

dependencies {
    api(project(":embrace-android-error-handling"))
    implementation(libs.androidx.annotation)
    testImplementation(project(":embrace-test-common"))
}

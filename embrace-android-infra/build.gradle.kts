plugins {
    id("embrace-prod-jvm-conventions")
}

description = "Embrace Android SDK: Infra"

dependencies {
    implementation(libs.androidx.annotation)
    testImplementation(project(":embrace-test-common"))
}

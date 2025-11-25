plugins {
    id("embrace-prod-jvm-conventions")
}

dependencies {
    implementation(project(":embrace-android-payload"))
    implementation(project(":embrace-android-infra"))

    testImplementation(project(":embrace-test-common"))
}

plugins {
    id("embrace-prod-jvm-conventions")
}

dependencies {
    implementation(project(":embrace-android-payload"))
    testImplementation(project(":embrace-android-config-fakes"))
}

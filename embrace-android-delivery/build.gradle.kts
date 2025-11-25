plugins {
    id("embrace-prod-jvm-conventions")
}

dependencies {
    implementation(platform(libs.okhttp.bom))
    implementation(libs.okhttp)
    implementation(project(":embrace-android-payload"))
    api(project(":embrace-android-telemetry-persistence"))
    implementation(project(":embrace-android-infra"))

    testImplementation(project(":embrace-test-common"))
    testImplementation(project(":embrace-android-delivery-fakes"))
    testImplementation(libs.mockwebserver)
    testImplementation(libs.mockk)
}

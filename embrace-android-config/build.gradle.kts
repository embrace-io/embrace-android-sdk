plugins {
    id("embrace-prod-jvm-conventions")
}

dependencies {
    implementation(project(":embrace-android-payload"))
    implementation(project(":embrace-android-infra"))
    implementation(platform(libs.okhttp.bom))
    implementation(libs.okhttp)

    testImplementation(project(":embrace-test-common"))
    testImplementation(project(":embrace-android-config-fakes"))
    testImplementation(libs.mockwebserver)
}

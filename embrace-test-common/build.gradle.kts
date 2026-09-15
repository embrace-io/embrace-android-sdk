plugins {
    id("embrace-jvm-conventions")
}

dependencies {
    implementation(project(":embrace-android-payload"))
    api(project(":embrace-android-infra"))
    implementation(platform(libs.okhttp.bom))
    implementation(libs.mockwebserver)
    implementation(libs.robolectric)
}

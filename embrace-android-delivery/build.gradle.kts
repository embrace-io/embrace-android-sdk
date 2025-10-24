plugins {
    kotlin("jvm")
    id("io.embrace.internal.build-logic")
    id("com.vanniktech.maven.publish")
}

dependencies {
    implementation(platform(libs.okhttp.bom))
    implementation(libs.okhttp)
    implementation(project(":embrace-android-payload"))
    implementation(project(":embrace-android-infra"))

    testImplementation(project(":embrace-test-common"))
    testImplementation(project(":embrace-android-delivery-fakes"))
    testImplementation(libs.mockwebserver)
    testImplementation(libs.mockk)
}

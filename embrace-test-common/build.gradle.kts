plugins {
    id("embrace-jvm-conventions")
}

dependencies {
    implementation(project(":embrace-android-payload"))
    implementation(project(":embrace-android-infra"))
    implementation(libs.opentelemetry.kotlin.semconv)
    implementation(platform(libs.okhttp.bom))
    implementation(libs.mockwebserver)
    implementation(libs.robolectric)
}

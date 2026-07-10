plugins {
    id("embrace-prod-jvm-conventions")
    id("org.jetbrains.kotlin.plugin.serialization")
    alias(libs.plugins.wire)
}

dependencies {
    implementation(platform(libs.okhttp.bom))
    implementation(libs.okhttp)
    implementation(libs.wire.runtime)
    implementation(project(":embrace-android-payload"))
    api(project(":embrace-android-telemetry-persistence"))
    implementation(project(":embrace-android-infra"))

    testImplementation(project(":embrace-test-common"))
    testImplementation(project(":embrace-android-delivery-fakes"))
    testImplementation(libs.mockwebserver)
    testImplementation(libs.mockk)
}

// generate Kotlin classes from src/main/proto.
wire {
    kotlin {}
}

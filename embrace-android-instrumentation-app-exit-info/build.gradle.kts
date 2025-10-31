plugins {
    id("com.android.library")
    kotlin("android")
    id("io.embrace.internal.build-logic")
    id("com.vanniktech.maven.publish")
}

description = "Embrace Android SDK: AEI"

android {
    namespace = "io.embrace.android.embracesdk.instrumentation.aei"
}

dependencies {
    implementation(project(":embrace-android-instrumentation-api"))
    implementation(project(":embrace-android-utils"))
    implementation(libs.androidx.annotation)

    testImplementation(project(":embrace-android-instrumentation-api-fakes"))
    testImplementation(project(":embrace-android-payload"))
    testImplementation(libs.protobuf.java)
    testImplementation(libs.protobuf.java.util)
    testImplementation(libs.mockk)
}

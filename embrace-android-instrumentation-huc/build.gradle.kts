plugins {
    id("embrace-prod-android-conventions")
}

description = "Embrace Android SDK: HttpUrlConnection instrumentation"

android {
    namespace = "io.embrace.android.embracesdk.instrumentation.huc"
    defaultConfig.consumerProguardFiles("embrace-proguard.cfg")
}

dependencies {

    compileOnly(project(":embrace-android-core"))
    compileOnly(project(":embrace-android-sdk"))
    compileOnly(project(":embrace-internal-api"))
    implementation(project(":embrace-android-instrumentation-api"))
    implementation(project(":embrace-android-instrumentation-network-common"))
    implementation(libs.androidx.annotation)

    testImplementation(project(":embrace-android-core"))
    testImplementation(project(":embrace-android-sdk"))
    testImplementation(project(":embrace-internal-api"))
    testImplementation(project(":embrace-test-fakes"))
    testImplementation(project(":embrace-android-instrumentation-api-fakes"))
    testImplementation(libs.mockk)
    testImplementation(libs.robolectric)
}

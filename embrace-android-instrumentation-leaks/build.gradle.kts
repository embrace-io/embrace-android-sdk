plugins {
    id("embrace-prod-android-conventions")
}

description = "Embrace Android SDK: Activity, Fragment and Fragment View Leak Detection"

android {
    namespace = "io.embrace.android.embracesdk.instrumentation.leaks"
    defaultConfig.consumerProguardFiles("embrace-proguard.cfg")
}

dependencies {
    implementation(project(":embrace-android-instrumentation-api"))
    compileOnly(libs.androidx.fragment)

    testImplementation(project(":embrace-android-instrumentation-api-fakes"))
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.fragment)
}

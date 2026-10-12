plugins {
    id("embrace-prod-android-conventions")
}

description = "Embrace Android SDK: Activity, Fragment, Fragment View, WebView and ViewModel Leak Detection"

android {
    namespace = "io.embrace.android.embracesdk.instrumentation.leaks"
    defaultConfig.consumerProguardFiles("embrace-proguard.cfg")
}

dependencies {
    implementation(project(":embrace-android-instrumentation-api"))
    compileOnly(libs.androidx.fragment)
    compileOnly(libs.lifecycle.viewmodel)

    testImplementation(project(":embrace-android-instrumentation-api-fakes"))
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.fragment)
    testImplementation(libs.lifecycle.viewmodel)
}

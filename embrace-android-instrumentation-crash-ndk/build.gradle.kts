plugins {
    id("embrace-prod-android-conventions")
}

description = "Embrace Android SDK: NDK Crash Instrumentation"

android {
    namespace = "io.embrace.android.embracesdk.instrumentation.crash.ndk"
    ndkVersion = "29.0.14206865"

    externalNativeBuild {
        cmake {
            path = file("CMakeLists.txt")
        }
    }
    packaging {
        jniLibs.pickFirsts.add("**/*.so")
    }
}

dependencies {
    implementation(project(":embrace-android-instrumentation-api"))
    implementation(project(":embrace-android-utils"))
    implementation(libs.opentelemetry.kotlin.semconv)

    testImplementation(project(":embrace-android-instrumentation-api-fakes"))
    testImplementation(project(":embrace-android-config-fakes"))
    testImplementation(libs.robolectric)
    testImplementation(libs.opentelemetry.kotlin.api)

    androidTestImplementation(libs.androidx.test.core)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestUtil(libs.androidx.test.orchestrator)
}

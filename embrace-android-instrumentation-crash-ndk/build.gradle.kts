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

    testImplementation(project(":embrace-android-instrumentation-api-fakes"))
    testImplementation(libs.robolectric)

    androidTestImplementation(libs.androidx.test.core)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestUtil(libs.androidx.test.orchestrator)
}

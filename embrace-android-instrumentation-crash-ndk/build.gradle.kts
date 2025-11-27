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
    defaultConfig.consumerProguardFiles("embrace-proguard.cfg")
}

dependencies {
    implementation(project(":embrace-android-instrumentation-api"))
    implementation(project(":embrace-android-utils"))
    implementation(project(":embrace-android-telemetry-persistence"))
    implementation(libs.opentelemetry.kotlin.api)
    implementation(libs.opentelemetry.kotlin.semconv)

    testImplementation(project(":embrace-android-instrumentation-api-fakes"))
    testImplementation(project(":embrace-test-fakes")) // TODO: remove this dependency after full modularisation
    testImplementation(libs.robolectric)

    androidTestImplementation(libs.androidx.test.core)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestUtil(libs.androidx.test.orchestrator)
}

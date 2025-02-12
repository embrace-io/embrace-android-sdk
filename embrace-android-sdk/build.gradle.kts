import io.embrace.internal.Versions

plugins {
    id("com.android.library")
    id("kotlin-android")
    id("io.embrace.internal.build-logic")
    id("com.google.devtools.ksp")
}

embrace {
    containsPublicApi.set(true)
}

description = "Embrace Android SDK: Core"

android {
    ndkVersion = Versions.NDK

    defaultConfig {
        namespace = "io.embrace.android.embracesdk"
        consumerProguardFiles("embrace-proguard.cfg")
    }

    externalNativeBuild {
        cmake {
            path = file("CMakeLists.txt")
        }
    }
    packaging {
        jniLibs.pickFirsts.add("**/*.so")
    }
}

kover {
    reports {
        filters {
            excludes {
                androidGeneratedClasses()
                classes("*.BuildConfig")
            }
        }
        variant("release") {
            xml {}
        }
    }
}

dependencies {
    api(project(":embrace-android-api"))
    implementation(project(":embrace-android-infra"))
    implementation(project(":embrace-android-core"))
    implementation(project(":embrace-android-features"))
    implementation(project(":embrace-android-payload"))
    implementation(project(":embrace-android-delivery"))
    implementation(project(":embrace-internal-api"))

    implementation(platform(libs.opentelemetry.bom))

    // lifecycle
    implementation(libs.lifecycle.runtime)
    implementation(libs.lifecycle.process)
    ksp(libs.lifecycle.compiler)
    testImplementation(libs.lifecycle.testing)

    // json
    implementation(libs.moshi)
    ksp(libs.moshi.kotlin.codegen)

    implementation(libs.opentelemetry.api)
    implementation(libs.opentelemetry.sdk)
    implementation(libs.opentelemetry.context)
    implementation(libs.opentelemetry.semconv)
    implementation(libs.opentelemetry.semconv.incubating)

    // ProfileInstaller 1.2.0 requires compileSdk 32. 1.1.0 requires compileSdk 31.
    // Please, don"t update it until we update compileSdk.
    implementation(libs.profileinstaller)

    testImplementation(project(":embrace-test-fakes"))
    testImplementation(libs.protobuf.java)
    testImplementation(libs.protobuf.java.util)
    testImplementation(libs.kotlin.reflect)
    testImplementation(libs.mockwebserver)

    androidTestImplementation(project(":embrace-test-fakes"))
}

project.tasks.register("publishLocal") { dependsOn("publishMavenPublicationToMavenLocal") }
project.tasks.register("publishQa") { dependsOn("publishMavenPublicationToQaRepository") }

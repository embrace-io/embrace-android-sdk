import io.embrace.gradle.Versions

plugins {
    id("embrace-prod-defaults")
    id("enable-explicit-api-mode")
    id("com.google.devtools.ksp")
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

// include these projects in code coverage
rootProject.childProjects.forEach { (_, proj) ->
    if (proj.plugins.hasPlugin("embrace-prod-defaults")) {
        dependencies.add("kover", proj)
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

    androidTestImplementation(project(":embrace-test-fakes"))
}

project.tasks.register("publishLocal") { dependsOn("publishMavenPublicationToMavenLocal") }
project.tasks.register("publishQa") { dependsOn("publishMavenPublicationToQaRepository") }

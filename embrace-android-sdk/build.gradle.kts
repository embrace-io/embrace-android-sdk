import io.embrace.gradle.Versions

plugins {
    id("embrace-prod-defaults")
    id("enable-explicit-api-mode")
    id("com.google.devtools.ksp")
}

description = "Embrace Android SDK: Core"

val version: String by project


android {
    ndkVersion = Versions.NDK

    defaultConfig {
        namespace = "io.embrace.android.embracesdk"
        consumerProguardFiles("embrace-proguard.cfg")

        // For library projects only, the BuildConfig.VERSION_NAME and BuildConfig.VERSION_CODE properties have been removed from the generated BuildConfig class
        //
        // https://developer.android.com/studio/releases/gradle-plugin#version_properties_removed_from_buildconfig_class_in_library_projects
        buildConfigField("String", "VERSION_NAME", "\"${version}\"")
        buildConfigField("String", "VERSION_CODE", "\"${53}\"")
    }

    externalNativeBuild {
        cmake {
            path = file("CMakeLists.txt")
        }
    }
    packagingOptions {
        pickFirst("**/*.so")
    }
    buildFeatures {
        buildConfig = true
    }
}

// include these projects in code coverage
dependencies {
    kover(project(":embrace-android-compose"))
    kover(project(":embrace-android-fcm"))
    kover(project(":embrace-android-okhttp3"))
    kover(project(":embrace-android-core"))
    kover(project(":embrace-android-features"))
    kover(project(":embrace-android-payload"))
    kover(project(":embrace-android-api"))
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
    implementation(project(":embrace-android-core"))
    implementation(project(":embrace-android-features"))
    implementation(project(":embrace-android-payload"))

    implementation(platform(libs.opentelemetry.bom))
    implementation(libs.lifecycle.common.java8)
    implementation(libs.lifecycle.process)

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

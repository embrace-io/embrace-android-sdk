import io.embrace.gradle.Versions
import org.jetbrains.dokka.base.DokkaBase
import org.jetbrains.dokka.base.DokkaBaseConfiguration
import org.jetbrains.dokka.gradle.DokkaTask

plugins {
    id("embrace-defaults")
    id("org.jetbrains.kotlin.kapt")
    id("org.jetbrains.dokka")
}

description = "Embrace Android SDK: Core"

val version: String by project


android {
    useLibrary("android.test.runner")
    useLibrary("android.test.base")
    useLibrary("android.test.mock")
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

    sourceSets {
        // Had to add a 'java' directory to store Java test files, as it doesn't get picked up as a test if I put it in
        // the kotlin directory. If I've just screwed up somehow and this is actually possible, please consolidate.
        getByName("test").java.srcDir("src/integrationTest/java")
        getByName("test").kotlin.srcDir("src/integrationTest/kotlin")
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
    implementation(platform(libs.opentelemetry.bom))
    implementation(libs.lifecycle.common.java8)
    implementation(libs.lifecycle.process)

    // json
    implementation(libs.moshi)
    kapt(libs.moshi.kotlin.codegen)

    implementation(libs.opentelemetry.api)
    implementation(libs.opentelemetry.sdk)
    implementation(libs.opentelemetry.context)
    implementation(libs.opentelemetry.semconv)
    implementation(libs.opentelemetry.semconv.incubating)

    // ProfileInstaller 1.2.0 requires compileSdk 32. 1.1.0 requires compileSdk 31.
    // Please, don"t update it until we update compileSdk.
    implementation(libs.profileinstaller)

    testImplementation(libs.mockk)
    testImplementation(libs.androidx.test.core)
    testImplementation(libs.androidx.test.junit)
    testImplementation(libs.robolectric)
    testImplementation(project(path = ":embrace-android-sdk"))
    testImplementation(libs.mockwebserver)
    testImplementation(libs.protobuf.java)
    testImplementation(libs.protobuf.java.util)
    testImplementation(libs.kotlin.reflect)

    dokkaHtmlPlugin(libs.dokka.convert)
    dokkaHtmlPlugin(libs.dokka.docs)

    // For the functional tests
    androidTestImplementation(libs.androidx.test.core)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(project(path = ":embrace-android-sdk"))
}

tasks.withType<DokkaTask>().configureEach {
    pluginConfiguration<DokkaBase, DokkaBaseConfiguration> {
        outputDirectory.set(rootProject.file("docs"))
        dokkaSourceSets {
            configureEach {
                perPackageOption {
                    skipDeprecated.set(false)
                    reportUndocumented.set(true) // Emit warnings about not documented members
                    includeNonPublic.set(false)

                    // Suppress files in the internal package
                    perPackageOption {
                        matchingRegex.set(".*.internal.*?")
                        suppress.set(true)
                    }
                }
            }
            named("main") {
                noAndroidSdkLink.set(false)
            }
        }
        suppressObviousFunctions.set(true)
    }
}

project.tasks.register("publishLocal") { dependsOn("publishMavenPublicationToMavenLocal") }
project.tasks.register("publishQa") { dependsOn("publishMavenPublicationToQaRepository") }

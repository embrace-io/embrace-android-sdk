import io.embrace.gradle.Versions
import org.jetbrains.dokka.base.DokkaBase
import org.jetbrains.dokka.base.DokkaBaseConfiguration
import org.jetbrains.dokka.gradle.DokkaTask

plugins {
    id("internal-embrace-plugin")
    id("org.jetbrains.kotlin.kapt")
    id("org.jetbrains.dokka")
}

description = "Embrace Android SDK: Core"

android {
    useLibrary("android.test.runner")
    useLibrary("android.test.base")
    useLibrary("android.test.mock")
    ndkVersion = Versions.ndk

    defaultConfig {
        namespace = "io.embrace.android.embracesdk"
        consumerProguardFiles("embrace-proguard.cfg")

        // For library projects only, the BuildConfig.VERSION_NAME and BuildConfig.VERSION_CODE properties have been removed from the generated BuildConfig class
        //
        // https://developer.android.com/studio/releases/gradle-plugin#version_properties_removed_from_buildconfig_class_in_library_projects
        buildConfigField("String", "VERSION_NAME", "\"${defaultConfig.versionName}\"")
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
    implementation("androidx.lifecycle:lifecycle-common-java8:${Versions.lifecycle}")
    implementation("androidx.lifecycle:lifecycle-process:${Versions.lifecycle}")

    // json
    implementation("com.squareup.moshi:moshi:${Versions.moshi}")
    kapt("com.squareup.moshi:moshi-kotlin-codegen:${Versions.moshi}")

    implementation("io.opentelemetry:opentelemetry-api:${Versions.openTelemetryCore}")
    implementation("io.opentelemetry:opentelemetry-sdk:${Versions.openTelemetryCore}")
    implementation("io.opentelemetry:opentelemetry-context:${Versions.openTelemetryCore}")
    implementation("io.opentelemetry.semconv:opentelemetry-semconv:${Versions.openTelementrySemConv}")
    implementation("io.opentelemetry.semconv:opentelemetry-semconv-incubating:${Versions.openTelementrySemConv}")

    // ProfileInstaller 1.2.0 requires compileSdk 32. 1.1.0 requires compileSdk 31.
    // Please, don"t update it until we update compileSdk.
    implementation("androidx.profileinstaller:profileinstaller:1.0.0")

    testImplementation("io.mockk:mockk:1.12.2")
    testImplementation("androidx.test:core:1.4.0")
    testImplementation("androidx.test.ext:junit:1.1.3")
    testImplementation("org.robolectric:robolectric:4.12.1")
    testImplementation(project(path = ":embrace-android-sdk"))
    testImplementation("com.squareup.okhttp3:mockwebserver:4.9.3")
    testImplementation("com.google.protobuf:protobuf-java:3.24.0")
    testImplementation("com.google.protobuf:protobuf-java-util:3.24.0")
    testImplementation("org.jetbrains.kotlin:kotlin-reflect:1.4.32")

    dokkaHtmlPlugin("org.jetbrains.dokka:kotlin-as-java-plugin:${Versions.dokka}")
    dokkaHtmlPlugin("org.jetbrains.dokka:android-documentation-plugin:${Versions.dokka}")

    // For the functional tests
    androidTestImplementation("androidx.test:core:1.4.0")
    androidTestImplementation("androidx.test:runner:1.4.0")
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

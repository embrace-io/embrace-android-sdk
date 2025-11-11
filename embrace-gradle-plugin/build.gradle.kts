plugins {
    id("embrace-jvm-conventions")
    id("java-gradle-plugin")
    id("groovy")
    id("com.vanniktech.maven.publish")
    alias(libs.plugins.google.ksp)
    alias(libs.plugins.buildconfig)
}

dependencies {
    compileOnly(libs.agp.api)
    compileOnly(gradleApi())
    compileOnly(kotlin("gradle-plugin"))

    implementation(platform(libs.okhttp.bom))
    implementation(libs.okhttp)
    implementation(libs.moshi)
    ksp(libs.moshi.kotlin.codegen)
    implementation(libs.zstd.jni)
    implementation(libs.asm.util)

    testImplementation(libs.agp.api)
    testImplementation(libs.junit)
    testImplementation(libs.mockk)
    testImplementation(libs.mockwebserver)
    testImplementation(project(":embrace-test-common"))
}

buildConfig {
    val version: String by project
    buildConfigField("String", "VERSION", "\"$version\"")
}

// marker artifact publication
gradlePlugin {
    plugins {
        create("embraceGradle") {
            id = "io.embrace.swazzler"
            group = "io.embrace"
            implementationClass = "io.embrace.android.gradle.plugin.EmbraceGradlePlugin"
            displayName = "Embrace Gradle Plugin"
            description = "The Embrace Gradle plugin uploads mapping information and instruments bytecode"
        }
    }
}

// Configure Vanniktech Maven Publish Plugin
mavenPublishing {
    coordinates("io.embrace", "embrace-swazzler", project.version.toString())

    publishToMavenCentral()

    // Only enable signing if credentials are available (e.g., in CI during publish or locally)
    if (project.hasProperty("signingInMemoryKey")) {
        signAllPublications()
    }

    pom {
        name = "embrace-swazzler"
        group = "io.embrace"
        description = "Embrace Gradle Plugin"
        url = "https://github.com/embrace-io/embrace-android-sdk"
        licenses {
            license {
                name = "Embrace License"
                url = "https://embrace.io/docs/terms-of-service/"
            }
        }
        developers {
            developer {
                id = "dev1"
                name = "Embrace"
                email = "support@embrace.io"
            }
        }
        scm {
            connection = "scm:git:github.com/embrace-io/embrace-android-sdk.git"
            developerConnection = "scm:git:ssh://github.com/embrace-io/embrace-android-sdk.git"
            url = "https://github.com/embrace-io/embrace-android-sdk/tree/main"
        }
    }
}

plugins {
    id("embrace-jvm-conventions")
    alias(libs.plugins.google.ksp)
    id("java-gradle-plugin")
    id("maven-publish")
}

dependencies {
    implementation(gradleApi())
    implementation(gradleTestKit())
    implementation(project(":embrace-gradle-plugin"))
    implementation(libs.agp.api)

    // JSON construction and parsing
    implementation(libs.moshi)
    ksp(libs.moshi.kotlin.codegen)

    implementation(libs.junit)
    implementation(platform(libs.okhttp.bom))
    implementation(libs.mockwebserver)
    implementation(libs.zstd.jni)
    implementation(libs.bundletool)
    implementation(libs.apktool.lib)

    testImplementation(project(":embrace-test-common"))
}

// Ensure all publishable modules are published to maven local before running integration tests
tasks.withType<Test>().configureEach {
    dependsOn(
        rootProject.subprojects
            .filter { it.plugins.hasPlugin(MavenPublishPlugin::class.java) }
            .map { it.tasks.named("publishToMavenLocal") }
    )

    // avoid default behavior of parallelisation as it can lead to resource exhaustion on CI (and locally)
    maxParallelForks = 1
}

group = "io.embrace"
version = project.properties["version"] as String

gradlePlugin {
    plugins {
        create("integrationTestPlugin") {
            id = "io.embrace.android.testplugin"
            implementationClass =
                "io.embrace.android.gradle.integration.framework.IntegrationTestPlugin"
        }
    }
}

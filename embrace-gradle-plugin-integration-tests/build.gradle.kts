plugins {
    id("embrace-jvm-conventions")
    id("org.jetbrains.kotlin.plugin.serialization")
    id("java-gradle-plugin")
    id("maven-publish")
}

dependencies {
    implementation(gradleApi())
    implementation(gradleTestKit())
    implementation(project(":embrace-gradle-plugin"))
    implementation(libs.agp.api)

    // JSON construction and parsing
    implementation(libs.kotlinx.serialization.json.gradle.plugin)

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

    // fixtures consume SDK artifacts from mavenLocal, which aren't inputs to this task. Always run the
    // tests so that SDK changes can't be hidden by a cached or up-to-date result.
    outputs.cacheIf("fixtures consume mavenLocal artifacts that aren't task inputs") { false }
    outputs.upToDateWhen { false }
}

// A single end-to-end build of an Android app that applies the plugin, used by the root 'verify' task.
tasks.register<Test>("sanityTest") {
    testClassesDirs = sourceSets.test.get().output.classesDirs
    classpath = sourceSets.test.get().runtimeClasspath
    filter.includeTestsMatching("io.embrace.android.gradle.integration.testcases.AndroidSimpleTest.assembleRelease")
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

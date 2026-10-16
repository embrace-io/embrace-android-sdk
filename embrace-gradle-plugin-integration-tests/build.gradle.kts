plugins {
    id("embrace-jvm-conventions")
    id("org.jetbrains.kotlin.plugin.serialization")
    id("java-gradle-plugin")
    id("maven-publish")
    id("embrace-integration-test-repo-conventions")
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
    implementation(libs.smali.baksmali)

    testImplementation(project(":embrace-test-common"))
}

val integrationRepo = fileTree(isolated.rootProject.projectDirectory.dir(INTEGRATION_TEST_REPO_PATH)) {
    // ignore timestamps in maven-metadata.xml and signatures
    exclude("**/maven-metadata.xml*", "**/*.asc")
}

tasks.withType<Test>().configureEach {
    // fixtures resolve Embrace artifacts from the integration test repo, so publish everything to it first
    dependsOn(integrationTestPublishedProjects.map { "$it:publishAllPublicationsToIntegrationTestRepository" })

    // declare everything the fixture builds read so that results can be cached. Repo file names contain a
    // unique timestamp per publish, so only content is fingerprinted.
    inputs.files(integrationRepo)
        .withPropertyName("integrationRepo")
        .withPathSensitivity(PathSensitivity.NONE)
    inputs.dir("fixtures")
        .withPropertyName("fixtures")
        .withPathSensitivity(PathSensitivity.RELATIVE)
    listOf(17, 21).forEach { version ->
        inputs.property(
            "jdk$version",
            providers.environmentVariable("JAVA_HOME_${version}_X64")
                .orElse(providers.environmentVariable("LOCAL_JAVA_${version}_PATH"))
                .orElse(""),
        )
    }

    systemProperty("embrace.maxVersion.agp", findVersion("agp"))
    systemProperty("embrace.maxVersion.gradle", gradle.gradleVersion)
    systemProperty("embrace.maxVersion.kotlin", findVersion("kotlinGradlePlugin"))
    systemProperty("embrace.maxVersion.compileSdk", findVersion("compileSdk"))

    maxParallelForks = providers.gradleProperty("integrationTestForks").map(String::toInt).getOrElse(2)
}

// A single end-to-end build of an Android app that applies the plugin, used by the root 'verify' task.
tasks.register<Test>("sanityTest") {
    testClassesDirs = sourceSets.test.get().output.classesDirs
    classpath = sourceSets.test.get().runtimeClasspath
    filter.includeTestsMatching("io.embrace.android.gradle.integration.testcases.AndroidSimpleTest.smoke")
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

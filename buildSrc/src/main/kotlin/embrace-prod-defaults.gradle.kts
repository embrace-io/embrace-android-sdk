import io.embrace.gradle.Versions
import org.gradle.api.tasks.testing.logging.TestExceptionFormat
import java.net.URI

plugins {
    id("embrace-test-defaults")
    id("checkstyle") apply false
    id("binary-compatibility-validator") apply false
    id("org.jetbrains.kotlinx.kover") apply false
    id("maven-publish") apply false
    id("signing") apply false
}

android {
    useLibrary("android.test.runner")
    useLibrary("android.test.base")
    useLibrary("android.test.mock")

    defaultConfig {
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        // The following argument makes the Android Test Orchestrator run its
        // "pm clear" command after each test invocation. This command ensures
        // that the app's state is completely cleared between tests.
        testInstrumentationRunnerArguments["clearPackageData"] = "true"

        aarMetadata {
            minCompileSdk = Versions.MIN_COMPILE_SDK
        }
    }

    testOptions {
        // Calling Android logging methods will throw exceptions if this is false
        // see: http://tools.android.com/tech-docs/unit-testing-support#TOC-Method-...-not-mocked.-
        unitTests.isReturnDefaultValues = true
        unitTests.isIncludeAndroidResources = true

        unitTests {
            all { test ->
                test.testLogging {
                    this.exceptionFormat = TestExceptionFormat.FULL
                }
                test.maxParallelForks = (Runtime.getRuntime().availableProcessors() / 3) + 1
            }
        }
        execution = "ANDROIDX_TEST_ORCHESTRATOR"
    }

    buildTypes {
        named("release") {
            isMinifyEnabled = false
        }
    }

    publishing {

        // create component with single publication variant
        // https://developer.android.com/studio/publish-library/configure-pub-variants#single-pub-var
        singleVariant("release") {
            withSourcesJar()
            withJavadocJar()
        }
    }

    sourceSets {
        getByName("test").java.srcDir("src/integrationTest/java")
        getByName("test").kotlin.srcDir("src/integrationTest/kotlin")
        getByName("test").resources.srcDir("src/integrationTest/resources")
    }
}

dependencies {
    implementation(findLibrary("kotlin.stdlib"))
    add("lintChecks", project.project(":embrace-lint"))

    testImplementation(findLibrary("junit"))
    testImplementation(findLibrary("mockk"))
    testImplementation(findLibrary("androidx.test.core"))
    testImplementation(findLibrary("androidx.test.junit"))
    testImplementation(findLibrary("robolectric"))
    testImplementation(findLibrary("mockwebserver"))
    testImplementation(project(":embrace-test-common"))
    testImplementation(project(":embrace-test-fakes"))

    androidTestImplementation(findLibrary("androidx.test.core"))
    androidTestImplementation(findLibrary("androidx.test.runner"))
    androidTestUtil(findLibrary("androidx.test.orchestrator"))
}

checkstyle {
    toolVersion = "10.3.2"
}

project.tasks.register("checkstyle", Checkstyle::class.java).configure {
    configFile = project.rootProject.file("config/checkstyle/google_checks.xml")
    ignoreFailures = false
    isShowViolations = true
    source("src")
    include("**/*.java")
    classpath = project.files()
    maxWarnings = 0
}

fun addPublication(publication: MavenPublication, componentName: String) = with(publication) {
    groupId = "io.embrace"
    artifactId = project.name
    version = project.version.toString()

    afterEvaluate {
        from(components[componentName])
    }

    // append some license metadata to the POM.
    pom {
        name = project.name
        description = "Embrace Android SDK"
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

// https://developer.android.com/studio/publish-library/upload-library
publishing {
    publications {
        register<MavenPublication>("release") {
            addPublication(this, "release")
        }
    }

    // configure repositories where the publication can be hosted
    repositories {
        // beta releases
        maven {
            credentials {
                username = System.getenv("MAVEN_QA_USER")
                password = System.getenv("MAVEN_QA_PASSWORD")
            }
            name = "Qa"
            url = URI.create("https://repo.embrace.io/repository/beta")
        }
        // the android-testing maven repository is used for publishing snapshots
        maven {
            credentials {
                username = System.getenv("MAVEN_QA_USER")
                password = System.getenv("MAVEN_QA_PASSWORD")
            }
            name = "Snapshot"
            url = URI.create("https://repo.embrace.io/repository/android-testing")
        }
    }
}

signing {
    val keyId = System.getenv("mavenSigningKeyId")
    val key = System.getenv("mavenSigningKeyRingFileEncoded")
    val password = System.getenv("mavenSigningKeyPassword")
    useInMemoryPgpKeys(keyId, key, password)
    sign(publishing.publications.getByName("release"))
}

project.tasks.withType(Sign::class).configureEach {
    enabled = !project.version.toString().endsWith("-SNAPSHOT")
}

// workaround: see https://medium.com/@saulmm2/android-gradle-precompiled-scripts-tomls-kotlin-dsl-df3c27ea017c
private fun Project.findLibrary(alias: String) =
    project.extensions.getByType<VersionCatalogsExtension>().named("libs").findLibrary(alias).get()

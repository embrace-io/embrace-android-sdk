import io.embrace.gradle.Versions
import io.gitlab.arturbosch.detekt.Detekt
import io.gitlab.arturbosch.detekt.DetektCreateBaselineTask
import org.gradle.api.tasks.testing.logging.TestExceptionFormat
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
import java.net.URI

plugins {
    id("com.android.library") apply false
    id("kotlin-android") apply false
    id("io.gitlab.arturbosch.detekt") apply false
    id("checkstyle") apply false
    id("binary-compatibility-validator") apply false
    id("org.jetbrains.kotlinx.kover") apply false
    id("maven-publish") apply false
    id("signing") apply false
}

android {
    compileSdk = Versions.COMPILE_SDK

    defaultConfig {
        minSdk = Versions.MIN_SDK
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        aarMetadata {
            minCompileSdk = Versions.MIN_SDK
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }

    lint {
        abortOnError = true
        warningsAsErrors = true
        checkAllWarnings = true
        checkReleaseBuilds = false // run on CI instead, speeds up release builds
        baseline = project.file("lint-baseline.xml")
        disable.addAll(mutableSetOf("GradleDependency", "NewerVersionAvailable"))
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
}

dependencies {
    testImplementation("junit:junit:${Versions.JUNIT}")
    implementation("org.jetbrains.kotlin:kotlin-stdlib:${Versions.KOTLIN_EXPOSED}")
    add("detektPlugins", "io.gitlab.arturbosch.detekt:detekt-formatting:${Versions.DETEKT}")
    add("lintChecks", project.project(":embrace-lint"))
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

detekt {
    buildUponDefaultConfig = true
    autoCorrect = true
    config.from(project.files("${project.rootDir}/config/detekt/detekt.yml")) // overwrite default behaviour here
    baseline =
        project.file("${project.projectDir}/config/detekt/baseline.xml") // suppress pre-existing issues
}

project.tasks.withType(Detekt::class.java).configureEach {
    jvmTarget = "1.8"
    reports {
        html.required.set(true)
        xml.required.set(false)
        txt.required.set(true)
        sarif.required.set(false)
        md.required.set(false)
    }
}

project.tasks.withType(DetektCreateBaselineTask::class.java).configureEach {
    jvmTarget = "1.8"
}

project.tasks.withType(JavaCompile::class.java).configureEach {
    options.compilerArgs.addAll(listOf("-Xlint:unchecked", "-Xlint:deprecation"))
}

project.tasks.withType(KotlinCompile::class.java).configureEach {
    kotlinOptions {
        apiVersion = "1.4"
        languageVersion = "1.4"
        jvmTarget = JavaVersion.VERSION_1_8.toString()
        freeCompilerArgs = freeCompilerArgs + "-Xexplicit-api=strict"

        // Targetting Kotlin 1.4 emits a warning that can't be suppressed.
        // Disabling this check for now.
        allWarningsAsErrors = false
    }
}

// https://developer.android.com/studio/publish-library/upload-library
publishing {
    publications {
        register<MavenPublication>("release") {
            groupId = "io.embrace"
            artifactId = project.name
            version = project.version.toString()

            afterEvaluate {
                from(components["release"])
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
    sign(publishing.publications.getByName("release"))
}

project.tasks.withType(Sign::class).configureEach {
    enabled = !project.version.toString().endsWith("-SNAPSHOT")
}

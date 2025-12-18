import org.gradle.api.tasks.testing.logging.TestExceptionFormat
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.dsl.KotlinVersion

plugins {
    kotlin("jvm")
    id("embrace-common-conventions")
}

dependencies {
    add("testImplementation", project.findLibrary("junit"))
}

val target = JvmTarget.JVM_11
val coreLibrariesVersion = project.findVersion("kotlinCoreLibrariesVersion")
val minKotlinVersion = KotlinVersion.KOTLIN_2_0

kotlin.compilerOptions {
    apiVersion.set(minKotlinVersion)
    languageVersion.set(minKotlinVersion)
    jvmTarget.set(target)
    allWarningsAsErrors.set(false)
}
kotlin.coreLibrariesVersion = coreLibrariesVersion

project.tasks.withType<Test>().configureEach {
    testLogging {
        exceptionFormat = TestExceptionFormat.FULL
    }
    maxParallelForks = (Runtime.getRuntime().availableProcessors() / 3) + 1
}

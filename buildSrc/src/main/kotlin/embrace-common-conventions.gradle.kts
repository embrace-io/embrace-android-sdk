import io.gitlab.arturbosch.detekt.Detekt
import io.gitlab.arturbosch.detekt.DetektCreateBaselineTask
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("io.gitlab.arturbosch.detekt")
}

/* configure detekt */

val target = project.findVersion("jvmTargetCompatibility")

detekt {
    buildUponDefaultConfig = true
    autoCorrect = true
    config.from(project.files("${project.rootDir}/config/detekt/detekt.yml"))
    baseline = project.file("${project.projectDir}/config/detekt/baseline.xml")
}

project.tasks.withType(Detekt::class.java).configureEach {
    jvmTarget = target
    reports {
        html.required.set(false)
        xml.required.set(false)
    }
}

project.tasks.withType(DetektCreateBaselineTask::class.java).configureEach {
    jvmTarget = target
}

dependencies {
    detektPlugins(project.findLibrary("detekt-formatting"))
}

/* configure compiler settings */

val compatVersion = project.findVersion("jvmTargetCompatibility")

project.tasks.withType(JavaCompile::class.java).configureEach {
    options.compilerArgs.addAll(listOf("-Xlint:unchecked", "-Xlint:deprecation"))
    sourceCompatibility = compatVersion
    targetCompatibility = compatVersion
}

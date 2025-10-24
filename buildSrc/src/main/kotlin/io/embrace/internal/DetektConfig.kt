package io.embrace.internal

import io.gitlab.arturbosch.detekt.Detekt
import io.gitlab.arturbosch.detekt.DetektCreateBaselineTask
import io.gitlab.arturbosch.detekt.extensions.DetektExtension
import org.gradle.api.Project

fun Project.configureDetekt() {
    val detekt = project.extensions.getByType(DetektExtension::class.java)
    val targetVersion = resolveVersionFromCatalog("jvmTargetCompatibility")

    detekt.apply {
        buildUponDefaultConfig = true
        autoCorrect = true
        config.from(project.files("${project.rootDir}/config/detekt/detekt.yml")) // overwrite default behaviour here
        baseline =
            project.file("${project.projectDir}/config/detekt/baseline.xml") // suppress pre-existing issues
    }
    project.tasks.withType(Detekt::class.java).configureEach {
        jvmTarget = targetVersion
        reports {
            html.required.set(false)
            xml.required.set(false)
            txt.required.set(false)
            sarif.required.set(false)
            md.required.set(false)
        }
    }
    project.tasks.withType(DetektCreateBaselineTask::class.java).configureEach {
        jvmTarget = targetVersion
    }
}

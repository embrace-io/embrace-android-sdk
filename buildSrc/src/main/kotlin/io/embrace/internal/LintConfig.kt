package io.embrace.internal

import com.android.build.api.dsl.LibraryExtension
import org.gradle.api.Project

fun LibraryExtension.configureLint(project: Project) {
    lint {
        abortOnError = true
        warningsAsErrors = true
        checkAllWarnings = true
        checkReleaseBuilds = false // run on CI instead, speeds up release builds
        baseline = project.file("lint-baseline.xml")
        disable.addAll(setOf("GradleDependency", "NewerVersionAvailable"))
    }
}

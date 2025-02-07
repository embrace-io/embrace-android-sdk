package io.embrace.internal

import org.gradle.api.Project
import org.gradle.api.plugins.quality.Checkstyle
import org.gradle.api.plugins.quality.CheckstyleExtension

fun Project.configureCheckstyle() {
    val checkstyle = project.extensions.getByType(CheckstyleExtension::class.java)
    checkstyle.toolVersion = "10.3.2"

    @Suppress("UnstableApiUsage")
    project.tasks.register("checkstyle", Checkstyle::class.java).configure {
        configFile = project.rootProject.file("config/checkstyle/google_checks.xml")
        ignoreFailures = false
        isShowViolations = true
        source("src")
        include("**/*.java")
        classpath = project.files()
        maxWarnings = 0
    }
}

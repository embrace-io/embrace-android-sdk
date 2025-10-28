package io.embrace.internal

import org.gradle.api.Project

@Suppress("unused")
fun Project.configureBinaryCompatValidation() {
    if (project.containsPublicApi()) {
        project.pluginManager.apply("binary-compatibility-validator")
    }
}

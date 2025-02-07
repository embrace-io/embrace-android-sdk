package io.embrace.internal

import kotlinx.validation.ApiValidationExtension
import org.gradle.api.Project

fun Project.configureBinaryCompatValidation(module: EmbraceBuildLogicExtension) {
    project.afterEvaluate {
        val apiValidation = project.extensions.getByType(ApiValidationExtension::class.java)
        apiValidation.validationDisabled = !module.containsPublicApi.get()
    }
}

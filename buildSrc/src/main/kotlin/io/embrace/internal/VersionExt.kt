package io.embrace.internal

import org.gradle.api.Project
import org.gradle.api.artifacts.VersionCatalogsExtension
import org.gradle.kotlin.dsl.getByType

fun Project.resolveVersionFromCatalog(key: String): String {
    val libs = extensions.getByType<VersionCatalogsExtension>().named("libs")
    return libs.findVersion(key).get().requiredVersion
}

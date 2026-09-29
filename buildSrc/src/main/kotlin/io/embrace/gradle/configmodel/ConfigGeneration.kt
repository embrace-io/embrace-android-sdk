package io.embrace.gradle.configmodel

import org.gradle.api.GradleException
import org.gradle.api.Project
import org.jetbrains.kotlin.gradle.dsl.KotlinProjectExtension
import java.io.File

private const val SCHEMA_PATH = "config-schema/embrace-config.yaml"
private const val JSON_SCHEMA_PATH = "embrace-config-schema.json"

internal fun parseConfigSchema(file: File, sourceName: String): ConfigSchema = try {
    ConfigSchemaParser(sourceName).parse(file.readText())
} catch (exc: IllegalArgumentException) {
    throw GradleException("Invalid config schema. ${exc.message}", exc)
}

/**
 * Generates this module's share of the config code from the config schema and adds it to the main source set.
 */
fun Project.generateConfigSources(target: ConfigTarget) {
    val task = tasks.register("generate${target.name.lowercase().replaceFirstChar { it.uppercaseChar() }}Config", GenerateConfigTask::class.java) {
        schemaFile.set(rootProject.layout.projectDirectory.file(SCHEMA_PATH))
        schemaPath.set(SCHEMA_PATH)
        this.target.set(target)
        outputDir.set(layout.buildDirectory.dir("generated/source/embraceConfig/main/kotlin"))
    }
    extensions.getByType(KotlinProjectExtension::class.java).sourceSets.named("main") {
        kotlin.srcDir(task)
    }
}

/**
 * Registers `configSchemaDump`, which writes embrace-config-schema.json at the root of the repository, and
 * `configSchemaCheck`, which `check` runs to fail the build when that file is out of date.
 */
fun Project.generateConfigJsonSchema() {
    val jsonSchema = rootProject.layout.projectDirectory.file(JSON_SCHEMA_PATH)
    tasks.register("configSchemaDump", ConfigJsonSchemaDumpTask::class.java) {
        schemaFile.set(rootProject.layout.projectDirectory.file(SCHEMA_PATH))
        schemaPath.set(SCHEMA_PATH)
        jsonSchemaFile.set(jsonSchema)
    }
    val check = tasks.register("configSchemaCheck", ConfigJsonSchemaCheckTask::class.java) {
        schemaFile.set(rootProject.layout.projectDirectory.file(SCHEMA_PATH))
        schemaPath.set(SCHEMA_PATH)
        jsonSchemaFile.set(jsonSchema)
        // A plain string: deriving it from the dump task would make this task depend on it and rewrite the file.
        dumpTaskPath.set("${project.path}:configSchemaDump")
    }
    tasks.named("check") { dependsOn(check) }
}

package io.embrace.gradle.configmodel

import org.gradle.api.DefaultTask
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction

/**
 * Writes embrace-config-schema.json from the config schema. The file is committed so that it can be published.
 */
abstract class ConfigJsonSchemaDumpTask : DefaultTask() {

    @get:InputFile
    @get:PathSensitive(PathSensitivity.NONE)
    abstract val schemaFile: RegularFileProperty

    @get:Input
    abstract val schemaPath: Property<String>

    @get:OutputFile
    abstract val jsonSchemaFile: RegularFileProperty

    @TaskAction
    fun dump() {
        val schema = parseConfigSchema(schemaFile.get().asFile, schemaPath.get())
        jsonSchemaFile.get().asFile.writeText(ConfigJsonSchemaRenderer(schema).render())
    }
}

package io.embrace.gradle.configmodel

import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction

/**
 * Fails if the committed embrace-config-schema.json doesn't match the config schema.
 */
abstract class ConfigJsonSchemaCheckTask : DefaultTask() {

    @get:InputFile
    @get:PathSensitive(PathSensitivity.NONE)
    abstract val schemaFile: RegularFileProperty

    @get:Input
    abstract val schemaPath: Property<String>

    @get:InputFile
    @get:PathSensitive(PathSensitivity.NONE)
    abstract val jsonSchemaFile: RegularFileProperty

    /** The task that rewrites the file, named in the failure message. */
    @get:Input
    abstract val dumpTaskPath: Property<String>

    @TaskAction
    fun check() {
        val schema = parseConfigSchema(schemaFile.get().asFile, schemaPath.get())
        val file = jsonSchemaFile.get().asFile
        if (file.readText() != ConfigJsonSchemaRenderer(schema).render()) {
            throw GradleException("${file.name} is out of date with ${schemaPath.get()}. Run ./gradlew ${dumpTaskPath.get()}")
        }
    }
}

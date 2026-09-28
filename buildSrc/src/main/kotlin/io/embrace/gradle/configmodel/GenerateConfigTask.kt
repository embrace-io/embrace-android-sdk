package io.embrace.gradle.configmodel

import org.gradle.api.DefaultTask
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction

/**
 * Generates a module's share of the config code from the config schema. See buildSrc/README.md for the format.
 */
@CacheableTask
abstract class GenerateConfigTask : DefaultTask() {

    @get:InputFile
    @get:PathSensitive(PathSensitivity.NONE)
    abstract val schemaFile: RegularFileProperty

    /** The schema's path as shown in generated file headers. */
    @get:Input
    abstract val schemaPath: Property<String>

    @get:Input
    abstract val target: Property<ConfigTarget>

    @get:OutputDirectory
    abstract val outputDir: DirectoryProperty

    @TaskAction
    fun generate() {
        val schema = parseConfigSchema(schemaFile.get().asFile, schemaPath.get())
        val root = outputDir.get().asFile.apply { deleteRecursively() }
        target.get().render(schema, schemaPath.get()).forEach { it.writeTo(root) }
    }
}

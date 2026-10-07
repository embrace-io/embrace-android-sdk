package io.embrace.config.codegen

import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
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
 * Generates the SDK's config code from the YAML definition.
 */
@CacheableTask
abstract class GenerateConfigTask : DefaultTask() {

    @get:InputFile
    @get:PathSensitive(PathSensitivity.NONE)
    abstract val yamlFile: RegularFileProperty

    @get:Input
    abstract val packageName: Property<String>

    @get:OutputDirectory
    abstract val sliceDir: DirectoryProperty

    @get:OutputDirectory
    abstract val testDir: DirectoryProperty

    @TaskAction
    fun generate() {
        val file = yamlFile.get().asFile
        val pkg = packageName.get()
        val (slices, tests) = try {
            val slices = ConfigSlice.fromYaml(file.readText())
            slices.map { configSliceFile(it, pkg, file.name) } to slices.map { configTestFile(it, pkg, file.name) }
        } catch (exc: IllegalArgumentException) {
            throw GradleException("${file.name}: ${exc.message}", exc)
        }
        val sliceRoot = sliceDir.get().asFile.apply { deleteRecursively() }
        val testRoot = testDir.get().asFile.apply { deleteRecursively() }
        slices.forEach { it.writeTo(sliceRoot) }
        tests.forEach { it.writeTo(testRoot) }
    }
}

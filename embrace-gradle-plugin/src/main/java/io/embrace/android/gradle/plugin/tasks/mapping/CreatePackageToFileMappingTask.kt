package io.embrace.android.gradle.plugin.tasks.mapping

import com.squareup.moshi.JsonClass
import io.embrace.android.gradle.plugin.tasks.EmbraceTaskImpl
import io.embrace.android.gradle.plugin.util.serialization.MoshiSerializer
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.model.ObjectFactory
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.SkipWhenEmpty
import org.gradle.api.tasks.TaskAction
import java.io.File
import javax.inject.Inject

abstract class CreatePackageToFileMappingTask @Inject constructor(
    objectFactory: ObjectFactory,
) : EmbraceTaskImpl(objectFactory) {

    private val serializer = MoshiSerializer()

    @get:SkipWhenEmpty
    @get:InputFiles
    val sourceFiles: ConfigurableFileCollection = objectFactory.fileCollection()

    @get:OutputFile
    val packageToFileMapJson: RegularFileProperty = objectFactory.fileProperty()

    @TaskAction
    fun generateMapping() {
        val mapping = getPackageToFileMap()

        // Write JSON
        val json = serializer.toJson(PackageToFileMapping(mapping), PackageToFileMapping::class.java)
        packageToFileMapJson.get().asFile.writeText(json)
    }

    private fun getPackageToFileMap(): Map<String, String> {
        val map = mutableMapOf<String, String>()
        sourceFiles.asFileTree.forEach { file ->
            when {
                file.name.endsWith(".java") -> map[getJavaPackageName(file)] = file.path

                file.name.endsWith(".kt") -> map[getKotlinPackageName(file)] = file.path
            }
        }
        return map
    }

    private fun getJavaPackageName(file: File): String {
        val content = file.bufferedReader().use { it.readText() }
        val classPackage = content.lineSequence()
            .find { it.trim().startsWith("package ") }
            ?.substringAfter("package ")
            ?.substringBefore(";")
            ?.trim() ?: ""
        return "$classPackage.${file.nameWithoutExtension}"
    }

    private fun getKotlinPackageName(file: File): String {
        val content = file.bufferedReader().use { it.readText() }
        val classPackage = content.lineSequence()
            .find { it.trim().startsWith("package ") }
            ?.substringAfter("package ")
            ?.trim() ?: return ""
        return "$classPackage.${file.nameWithoutExtension}"
    }

    companion object {
        const val NAME = "createPackageToFileMappingTask"
    }
}

@JsonClass(generateAdapter = true)
data class PackageToFileMapping(
    val classes: Map<String, String>,
)

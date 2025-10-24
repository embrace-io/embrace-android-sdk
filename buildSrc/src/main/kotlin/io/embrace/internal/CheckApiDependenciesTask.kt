package io.embrace.internal

import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.provider.Property
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import java.io.File
import java.util.zip.ZipInputStream
import kotlin.metadata.jvm.JvmMetadataVersion
import kotlin.metadata.jvm.KotlinModuleMetadata
import kotlin.metadata.jvm.UnstableMetadataApi

/**
 * Task that checks API dependencies for incompatible Kotlin metadata versions.
 *
 * This validates that transitive dependencies don't expose Kotlin metadata compiled
 * with a version incompatible with your configured Kotlin version.
 */
@CacheableTask
abstract class CheckApiDependenciesTask : DefaultTask() {

    @get:Input
    abstract val kotlinVersion: Property<String>

    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val compileClasspath: ConfigurableFileCollection

    @get:Input
    abstract val projectPath: Property<String>

    // This file allows gradle to cache the task.
    @get:OutputFile
    abstract val outputFile: Property<File>

    init {
        group = "verification"
        description = "Checks that API dependencies don't expose incompatible Kotlin metadata"
    }

    @OptIn(UnstableMetadataApi::class)
    @TaskAction
    fun check() {
        val version = kotlinVersion.get()
        val supportedVersion = calculateSupportedVersion(version)
        var hasErrors = false

        compileClasspath.files.forEach { file ->
            if (file.exists() && file.extension == "jar") {
                file.forEachModuleInfoFile { name, bytes ->
                    try {
                        val metadata = KotlinModuleMetadata.read(bytes)
                        if (metadata.version > supportedVersion) {
                            val message = """
                                Unsupported Kotlin metadata found in ${file.path}:$name
                                  Found version: ${metadata.version}
                                  Expected: $version (supports up to $supportedVersion)
                                  Run './gradlew ${projectPath.get()}:dependencies' to investigate the dependency tree.
                            """.trimIndent()

                            logger.error(message)
                            hasErrors = true
                        }
                    } catch (e: Exception) {
                        logger.debug("Failed to read Kotlin metadata from ${file.path}:$name", e)
                    }
                }
            }
        }

        if (hasErrors) {
            throw GradleException("Found dependencies with incompatible Kotlin metadata. See errors above.")
        }

        outputFile.get().writeText("Check completed at ${System.currentTimeMillis()}")
    }

    private fun calculateSupportedVersion(kotlinVersion: String): JvmMetadataVersion {
        val parts = kotlinVersion.split(".")
        require(parts.size >= 2) { "Cannot parse Kotlin version $kotlinVersion. Expected format is X.Y.Z" }

        var major = parts[0].toInt()
        var minor = parts[1].toInt()

        if (major == 1 && minor == 9) {
            // Kotlin 1.9 can read 2.0 metadata
            major = 2
            minor = 0
        } else {
            // n + 1 forward compatibility in the general case
            minor += 1
        }

        return JvmMetadataVersion(major, minor, 0)
    }

    private fun File.forEachModuleInfoFile(block: (String, ByteArray) -> Unit) {
        ZipInputStream(inputStream()).use { zis ->
            var entry = zis.nextEntry
            while (entry != null) {
                if (entry.name.matches(Regex("META-INF/.*\\.kotlin_module"))) {
                    block(entry.name, zis.readBytes())
                }
                entry = zis.nextEntry
            }
        }
    }
}

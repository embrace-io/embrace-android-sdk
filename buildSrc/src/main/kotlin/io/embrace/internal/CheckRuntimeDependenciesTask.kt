package io.embrace.internal

import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.*
import java.io.File

/**
 * Task that checks runtime dependencies for incompatible kotlin-stdlib versions.
 *
 * This validates that transitive dependencies don't rely on a kotlin-stdlib version higher than the configured Kotlin version.
 */
@CacheableTask
abstract class CheckRuntimeDependenciesTask : DefaultTask() {

    @get:Input
    abstract val kotlinVersion: Property<String>

    @get:Input
    abstract val transitiveKotlinStdlibVersions: ListProperty<String>

    @get:Input
    abstract val projectPath: Property<String>

    @get:Input
    abstract val configurationName: Property<String>

    // This file allows gradle to cache the task.
    @get:OutputFile
    abstract val outputFile: Property<File>

    init {
        group = "verification"
        description = "Checks that runtime dependencies don't use incompatible kotlin-stdlib versions"
    }

    @TaskAction
    fun check() {
        val version = kotlinVersion.get()
        val supportedVersion = version.toMinorVersion()
        var hasErrors = false

        transitiveKotlinStdlibVersions.get().forEach { depVersion ->
            val depMinorVersion = depVersion.toMinorVersion()
            if (depMinorVersion > supportedVersion) {
                val message = """
                    Incompatible kotlin-stdlib version found: $depVersion
                      Maximum supported: $supportedVersion
                      Run './gradlew ${projectPath.get()}:dependencies --configuration ${configurationName.get()}' to investigate the dependency tree.
                      (Look for org.jetbrains.kotlin:kotlin-stdlib:$depVersion)
                """.trimIndent()

                logger.error(message)
                hasErrors = true
            }
        }

        if (hasErrors) {
            throw GradleException("Found dependencies with incompatible kotlin-stdlib versions. See errors above.")
        }

        outputFile.get().writeText("Check completed at ${System.currentTimeMillis()}")
    }

    private data class MinorVersion(val major: Int, val minor: Int) : Comparable<MinorVersion> {
        override fun toString(): String = "$major.$minor"

        override fun compareTo(other: MinorVersion): Int {
            return compareValuesBy(this, other, { it.major }, { it.minor })
        }
    }

    private fun String.toMinorVersion(): MinorVersion {
        val parts = split(".")
        require(parts.size >= 2) {
            "Cannot parse Kotlin version '$this'. Expected format is major.minor.{extra}"
        }
        return MinorVersion(parts[0].toInt(), parts[1].toInt())
    }
}

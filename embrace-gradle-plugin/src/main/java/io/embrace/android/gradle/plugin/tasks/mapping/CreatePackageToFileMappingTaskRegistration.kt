package io.embrace.android.gradle.plugin.tasks.mapping

import io.embrace.android.gradle.plugin.gradle.registerTask
import io.embrace.android.gradle.plugin.tasks.registration.EmbraceTaskRegistration
import io.embrace.android.gradle.plugin.tasks.registration.RegistrationParams
import io.embrace.android.gradle.plugin.util.capitalizedString

/**
 * Registers the [CreatePackageToFileMappingTask] for each build variant.
 *
 * This registration collects all Java and Kotlin source files from the variant and configures
 * the task to output the package-to-file mapping JSON to the build directory. It uses the
 * preBuild task as an anchor.
 */
class CreatePackageToFileMappingTaskRegistration : EmbraceTaskRegistration {
    override fun register(params: RegistrationParams) {
        params.execute()
    }

    fun RegistrationParams.execute() {
        val mappingTask = project.registerTask(
            CreatePackageToFileMappingTask.NAME,
            CreatePackageToFileMappingTask::class.java,
            data
        ) { task ->
            // Add Java sources if available
            variant.sources.java?.let { java ->
                task.sourceFiles.from(java.all)
            }

            // Add Kotlin sources if available
            variant.sources.kotlin?.let { kotlin ->
                task.sourceFiles.from(kotlin.all)
            }
            task.packageToFileMapJson.set(
                project.layout.buildDirectory.file("outputs/embrace/mapping/${variant.name}/package-mapping.json")
            )
        }
        project.tasks.named("pre${variant.name.capitalizedString()}Build").configure {
            it.dependsOn(mappingTask)
        }
    }
}

package io.embrace.android.gradle.plugin.tasks.mapping

import io.embrace.android.gradle.plugin.gradle.registerTask
import io.embrace.android.gradle.plugin.tasks.registration.EmbraceTaskRegistration
import io.embrace.android.gradle.plugin.tasks.registration.RegistrationParams
import io.embrace.android.gradle.plugin.util.capitalizedString

class CreatePackageToFileMappingTaskRegistration : EmbraceTaskRegistration {
    override fun register(params: RegistrationParams) {
        params.execute()
    }

    fun RegistrationParams.execute() {
        val mappingTask = project.registerTask(
            CreatePackageToFileMappingTask.NAME,
            CreatePackageToFileMappingTask::class.java,
            data
        ) {
            // Add Java sources if available
            variant.sources.java?.let { java ->
                it.sourceFiles.from(java.all)
            }

            // Add Kotlin sources if available
            variant.sources.kotlin?.let { kotlin ->
                it.sourceFiles.from(kotlin.all)
            }
            it.packageToFileMapJson.set(
                project.layout.buildDirectory.file("outputs/mapping/${variant.name}/package-mapping.json")
            )
        }

        project.afterEvaluate {
            // Make preBuild depend on our task
            project.tasks.named("javaPreCompile${variant.name.capitalizedString()}").configure {
                it.dependsOn(mappingTask)
            }
        }
    }
}

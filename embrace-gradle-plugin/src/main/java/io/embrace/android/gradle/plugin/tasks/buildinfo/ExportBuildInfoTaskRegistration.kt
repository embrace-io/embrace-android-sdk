package io.embrace.android.gradle.plugin.tasks.buildinfo

import io.embrace.android.gradle.plugin.gradle.registerTask
import io.embrace.android.gradle.plugin.tasks.registration.MappingTaskRegistration
import io.embrace.android.gradle.plugin.tasks.registration.RegistrationParams
import org.gradle.api.Task
import org.gradle.api.tasks.TaskProvider

/**
 * Registers a task per variant that exports the parameters that would be used to upload the
 * mapping file and NDK symbols into an `embrace-build-info.json` file, controlled via
 * `embrace.exportBuildInfo`.
 */
class ExportBuildInfoTaskRegistration : MappingTaskRegistration() {

    override fun registerForMappingTask(params: RegistrationParams, anchorTask: TaskProvider<Task>) {
        val anchorTaskWithoutVariant = anchorTaskNameWithoutVariant(anchorTask, params.data)
        val exportTaskName = "${ExportBuildInfoTask.NAME}${anchorTaskWithoutVariant}OnVariant"
        val exportTask = params.project.registerTask(
            exportTaskName,
            ExportBuildInfoTask::class.java,
            params.data,
        ) { task ->
            val variantConfig = params.variantConfigurationsListProperty.get().first { it.variantName == params.data.name }
            val embraceConfig = variantConfig.embraceConfig

            task.buildId.set(params.buildIdProvider)
            task.appId.set(embraceConfig?.appId.orEmpty())
            task.apiToken.set(embraceConfig?.apiToken.orEmpty())

            task.outputFile.set(
                params.project.layout.buildDirectory.file(
                    "outputs/embrace/build-info/${params.data.name}/${ExportBuildInfoTask.FILE_NAME}",
                ),
            )
        }

        exportTask.finalizeAnchorTask(anchorTask)
    }
}

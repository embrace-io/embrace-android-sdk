package io.embrace.android.gradle.plugin.tasks.buildinfo

import io.embrace.android.gradle.plugin.gradle.isTaskRegistered
import io.embrace.android.gradle.plugin.gradle.registerTask
import io.embrace.android.gradle.plugin.gradle.tryGetTaskProvider
import io.embrace.android.gradle.plugin.instrumentation.config.model.VariantConfig
import io.embrace.android.gradle.plugin.model.AndroidCompactedVariantData
import io.embrace.android.gradle.plugin.tasks.registration.EmbraceTaskRegistration
import io.embrace.android.gradle.plugin.tasks.registration.RegistrationParams
import io.embrace.android.gradle.plugin.util.capitalizedString
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.TaskProvider

/**
 * Registers a task per variant that exports the parameters that would be used to upload the
 * mapping file and NDK symbols into an `embrace-build-info.json` file, controlled via
 * `embrace.exportBuildInfo`.
 */
class ExportBuildInfoTaskRegistration : EmbraceTaskRegistration {

    override fun register(params: RegistrationParams) {
        with(params) {
            project.afterEvaluate {
                val tasks = fetchObfuscationTasks(project, data)
                tasks.forEach { task ->
                    register(
                        project,
                        data,
                        task,
                        variantConfigurationsListProperty,
                        buildIdProvider,
                    )
                }
            }
        }
    }

    private fun register(
        project: Project,
        variant: AndroidCompactedVariantData,
        anchorTask: TaskProvider<Task>,
        variantConfigurationsListProperty: ListProperty<VariantConfig>,
        buildIdProvider: Provider<String>,
    ): TaskProvider<ExportBuildInfoTask> {
        val anchorTaskWithoutVariant = anchorTask.name.replace(variant.name, "", true)
        val exportTaskName = "${ExportBuildInfoTask.NAME}${anchorTaskWithoutVariant}OnVariant"
        val exportTask = project.registerTask(
            exportTaskName,
            ExportBuildInfoTask::class.java,
            variant,
        ) { task ->
            val variantConfig = variantConfigurationsListProperty.get().first { it.variantName == variant.name }
            val embraceConfig = variantConfig.embraceConfig

            task.buildId.set(buildIdProvider)
            task.appId.set(embraceConfig?.appId.orEmpty())
            task.apiToken.set(embraceConfig?.apiToken.orEmpty())

            task.outputFile.set(
                project.layout.buildDirectory.file(
                    "outputs/embrace/build-info/${variant.name}/${ExportBuildInfoTask.FILE_NAME}",
                ),
            )
        }

        anchorTask.configure { task ->
            task.finalizedBy(exportTask)
        }
        return exportTask
    }

    /**
     * It fetches all available obfuscation tasks. In practice, a single task will be
     * executed but multiple tasks may be registered and configured. Because of this, we are
     * forced to return a list of tasks since we are in configuration phase.
     */
    private fun fetchObfuscationTasks(
        project: Project,
        variant: AndroidCompactedVariantData,
    ): List<TaskProvider<Task>> {
        val name = variant.name.capitalizedString()
        val targetObfuscationTasks = listOf(
            "dexguardApk$name",
            "dexguardAab$name",
            "minify${name}WithProguard",
            "minify${name}WithR8",
        )
        val tasks = targetObfuscationTasks.filter { taskName ->
            isTaskRegistered(project.tryGetTaskProvider(taskName))
        }
        return tasks.mapNotNull { project.tryGetTaskProvider(it) }
    }
}

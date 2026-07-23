package io.embrace.android.gradle.plugin.tasks.registration

import io.embrace.android.gradle.plugin.gradle.isTaskRegistered
import io.embrace.android.gradle.plugin.gradle.tryGetTaskProvider
import io.embrace.android.gradle.plugin.model.AndroidCompactedVariantData
import io.embrace.android.gradle.plugin.util.capitalizedString
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.tasks.TaskProvider

/**
 * Abstract base class for [EmbraceTaskRegistration] implementations that register a task per
 * mapping task, finalized by that anchor task.
 */
abstract class MappingTaskRegistration : EmbraceTaskRegistration {

    final override fun register(params: RegistrationParams) {
        params.project.afterEvaluate {
            fetchMappingTasks(params.project, params.data).forEach { anchorTask ->
                registerForMappingTask(params, anchorTask)
            }
        }
    }

    /**
     * Registers the task associated with a single mapping task.
     */
    protected abstract fun registerForMappingTask(
        params: RegistrationParams,
        anchorTask: TaskProvider<Task>,
    )

    /**
     * Strips the variant name from the anchor task's name, so it can be reused as a stable
     * fragment when naming derived tasks.
     */
    protected fun anchorTaskNameWithoutVariant(
        anchorTask: TaskProvider<Task>,
        variant: AndroidCompactedVariantData,
    ): String = anchorTask.name.replace(variant.name, "", true)

    /**
     * Sets us (Embrace) as a dependency of the given anchor (obfuscation) task.
     */
    protected fun TaskProvider<out Task>.finalizeAnchorTask(anchorTask: TaskProvider<Task>) {
        anchorTask.configure { task -> task.finalizedBy(this@finalizeAnchorTask) }
    }

    private fun fetchMappingTasks(
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

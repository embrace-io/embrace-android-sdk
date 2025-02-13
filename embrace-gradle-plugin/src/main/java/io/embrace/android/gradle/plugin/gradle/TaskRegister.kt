package io.embrace.android.gradle.plugin.gradle

import io.embrace.android.gradle.plugin.model.AndroidCompactedVariantData
import io.embrace.android.gradle.plugin.tasks.EmbraceTask
import io.embrace.android.gradle.plugin.util.capitalizedString
import org.gradle.api.Action
import org.gradle.api.Project
import org.gradle.api.tasks.TaskProvider

/**
 * Registers an embrace task on the project and sets initial values on task properties.
 */
fun <T : EmbraceTask> Project.registerTask(
    name: String,
    clz: Class<T>,
    variantData: AndroidCompactedVariantData,
    configurationAction: Action<T>
): TaskProvider<T> {
    val taskName = "$name${variantData.name.capitalizedString()}"
    logger.info("Registering task=$taskName")
    val taskProvider: TaskProvider<T> = tasks.register(taskName, clz) { task: T ->
        // at this point, it means that the task has been realized, which is ok, it means that the task is being
        // configured (not necessarily ran)
        logger.info("Task=$taskName has been realized.")
        task.variantData.set(variantData)
        configurationAction.execute(task)
        logger.debug("Task=$taskName configured.")
    }
    return taskProvider
}

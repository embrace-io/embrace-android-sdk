package io.embrace.android.gradle.plugin.gradle

import io.embrace.android.gradle.plugin.util.capitalizedString
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.tasks.TaskContainer
import org.gradle.api.tasks.TaskProvider

/**
 * @return if the task is already registered in the gradle task graph.
 */
fun Project.isTaskRegistered(taskName: String, variantName: String): Boolean {
    return tasks.names.contains("$taskName${variantName.capitalizedString()}")
}

fun TaskContainer.isTaskRegistered(taskName: String, variantName: String): Boolean {
    return names.contains("$taskName${variantName.capitalizedString()}")
}

/**
 * It determines if given taskProvider is registered.
 *
 * This method could be removed, it is here for better comprehension.
 */
fun isTaskRegistered(taskProvider: TaskProvider<Task>?) = taskProvider != null

/**
 * It returns task provider for given taskName without realizing the task.
 */
fun Project.tryGetTaskProvider(taskName: String): TaskProvider<Task>? {
    return try {
        tasks.named(taskName)
    } catch (e: Exception) {
        null
    }
}

/**
 * Returns a task provider for given taskName and type without realizing the task.
 */
fun <T : Task> Project.tryGetTaskProvider(taskName: String, taskType: Class<T>): TaskProvider<T>? {
    return try {
        tasks.named(taskName, taskType)
    } catch (e: Exception) {
        null
    }
}

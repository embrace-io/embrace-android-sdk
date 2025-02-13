package io.embrace.android.gradle.plugin.tasks.il2cpp

import io.embrace.android.gradle.plugin.gradle.tryGetTaskProvider
import io.embrace.android.gradle.plugin.model.AndroidCompactedVariantData
import io.embrace.android.gradle.plugin.tasks.ndk.NdkUploadTask
import io.embrace.android.gradle.plugin.util.capitalizedString
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.tasks.TaskProvider

/**
 * Fetches the task the IL2CPP compression + upload tasks will depend on. This is currently
 * the NDK upload task, because Unity should always be using the NDK to build IL2CPP.
 */
class Il2CppTaskSource {
    fun fetchTask(project: Project, variant: AndroidCompactedVariantData): TaskProvider<Task>? {
        val taskName = "${NdkUploadTask.NAME}${variant.name.capitalizedString()}"
        return project.tryGetTaskProvider(taskName)
    }
}

package io.embrace.android.gradle.plugin.agp

import org.gradle.api.Task
import org.gradle.api.tasks.TaskProvider

object AgpUtils {

    private const val MINIMUM_DESUGARING_LEVEL = 23
    fun isDesugaringRequired(sdkLevel: Int) = sdkLevel <= MINIMUM_DESUGARING_LEVEL

    /**
     * It determines if given task is involved in dexguard framework.
     */
    fun isDexguard(nativeObfuscationTask: TaskProvider<Task>) =
        nativeObfuscationTask.name.lowercase().contains("dexguard")
}

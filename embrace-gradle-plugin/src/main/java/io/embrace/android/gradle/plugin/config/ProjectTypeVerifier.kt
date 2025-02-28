package io.embrace.android.gradle.plugin.config

import io.embrace.android.gradle.plugin.agp.AgpWrapper
import io.embrace.android.gradle.plugin.gradle.isTaskRegistered
import org.gradle.api.Project
import org.gradle.api.provider.Provider

internal object ProjectTypeVerifier {

    @JvmStatic
    fun getProjectType(
        unitySymbolsDir: Provider<UnitySymbolsDir>,
        agpWrapper: AgpWrapper,
        behavior: PluginBehavior,
        variantName: String,
        project: Project,
    ): ProjectType {
        return when {
            isNative(agpWrapper, behavior, variantName, project) -> ProjectType.NATIVE
            isUnity(unitySymbolsDir.orNull) -> ProjectType.UNITY
            else -> ProjectType.OTHER
        }
    }

    private fun isUnity(unitySymbolsDir: UnitySymbolsDir?): Boolean {
        return unitySymbolsDir != null && unitySymbolsDir.isDirPresent()
    }

    /**
     * Check if it's a native project
     */
    private fun isNative(
        agpWrapper: AgpWrapper,
        behavior: PluginBehavior,
        variantName: String,
        project: Project,
    ): Boolean {
        return agpWrapper.usesCMake || agpWrapper.usesNdkBuild || hasExternalNativeTask(variantName, project) ||
            usesCustomNativeBuild(behavior)
    }

    private fun usesCustomNativeBuild(behavior: PluginBehavior) = !behavior.customSymbolsDirectory.isNullOrEmpty()

    private fun hasExternalNativeTask(variantName: String, project: Project) = project.isTaskRegistered(
        "externalNativeBuild",
        variantName
    )
}

package io.embrace.android.gradle.plugin.config

import io.embrace.android.gradle.plugin.agp.AgpWrapper
import org.gradle.api.provider.Provider

internal object ProjectTypeVerifier {

    @JvmStatic
    fun getProjectType(
        unitySymbolsDir: Provider<UnitySymbolsDir>,
        agpWrapper: AgpWrapper,
        behavior: PluginBehavior,
    ): ProjectType {
        return when {
            isNative(agpWrapper, behavior) -> ProjectType.NATIVE
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
        behavior: PluginBehavior
    ): Boolean {
        return agpWrapper.usesCMake || agpWrapper.usesNdkBuild ||
            usesCustomNativeBuild(behavior)
    }

    private fun usesCustomNativeBuild(behavior: PluginBehavior): Boolean {
        return !behavior.customSymbolsDirectory.isNullOrEmpty()
    }
}

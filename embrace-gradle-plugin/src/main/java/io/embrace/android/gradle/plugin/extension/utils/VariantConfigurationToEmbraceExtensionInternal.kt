package io.embrace.android.gradle.plugin.extension.utils

import io.embrace.android.gradle.plugin.agp.AgpWrapper
import io.embrace.android.gradle.plugin.config.PluginBehavior
import io.embrace.android.gradle.plugin.config.ProjectTypeVerifier
import io.embrace.android.gradle.plugin.config.UnitySymbolsDir
import io.embrace.android.gradle.plugin.extension.EmbraceExtensionInternal
import io.embrace.android.gradle.plugin.instrumentation.config.model.VariantConfig
import io.embrace.android.gradle.plugin.model.AndroidCompactedVariantData
import io.embrace.android.gradle.plugin.tasks.il2cpp.UnitySymbolFilesManager
import org.gradle.api.Project
import org.gradle.api.provider.Provider

private const val NDK_ENABLED_DEFAULT: Boolean = true

/**
 * It configures this extension with given VariantConfiguration properties.
 */
class VariantConfigurationToEmbraceExtensionInternal(
    private val variantInfo: AndroidCompactedVariantData,
    private val variantConfigProvider: Provider<VariantConfig>,
    private val agpWrapper: AgpWrapper,
    private val behavior: PluginBehavior,
    private val project: Project
) : BaseVariantToEmbraceExtensionInternal(variantInfo.name) {

    override fun setupVariant(extension: EmbraceExtensionInternal) {
        extension.variants.named(variantInfo.name).configure {
            // properties from variant configuration
            it.apiToken.set(
                variantConfigProvider.map { variantConfig ->
                    variantConfig.embraceConfig?.apiToken ?: ""
                }
            )
            it.ndkEnabled.set(
                variantConfigProvider.map { variantConfig ->
                    variantConfig.embraceConfig?.ndkEnabled ?: NDK_ENABLED_DEFAULT
                }
            )
            it.appId.set(
                variantConfigProvider.map { variantConfig ->
                    variantConfig.embraceConfig?.appId ?: ""
                }
            )
            it.config.set(variantConfigProvider)
            it.buildId.set(
                variantConfigProvider.map { variantConfig ->
                    variantConfig.buildId ?: ""
                }
            )

            val symbolsDir = getSymbolsDir()
            val projectType = getProjectType(symbolsDir)

            it.unitySymbolsDir.set(symbolsDir)
            it.projectType.set(projectType)
        }
    }

    // there is no need to let Gradle know about the knowledge of how to get unitySymbolsDir, because
    // all properties that getSymbolsDir depends on are already config-cache aware. Meaning that if any
    // of the properties that this function uses changes, Gradle will invalidate config cache.
    private fun getSymbolsDir(): Provider<UnitySymbolsDir> = project.provider {
        val unityConfig = if (variantConfigProvider.isPresent) {
            variantConfigProvider.get().embraceConfig?.unityConfig
        } else {
            null
        }

        val realProject = project.parent ?: project
        UnitySymbolFilesManager.of().getSymbolsDir(
            realProject.layout.projectDirectory,
            project.layout.projectDirectory,
            unityConfig
        )
    }

    private fun getProjectType(symbolsDir: Provider<UnitySymbolsDir>) = project.provider {
        ProjectTypeVerifier.getProjectType(
            symbolsDir,
            agpWrapper,
            behavior,
        )
    }
}

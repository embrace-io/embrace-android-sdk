package io.embrace.android.gradle.plugin.tasks.registration

import com.android.build.api.variant.AndroidComponentsExtension
import com.android.build.api.variant.Variant
import io.embrace.android.gradle.plugin.agp.AgpWrapper
import io.embrace.android.gradle.plugin.config.PluginBehavior
import io.embrace.android.gradle.plugin.config.ProjectTypeVerifier
import io.embrace.android.gradle.plugin.config.UnitySymbolsDir
import io.embrace.android.gradle.plugin.config.variant.EmbraceVariantConfigurationBuilder
import io.embrace.android.gradle.plugin.dependency.installDependenciesForVariant
import io.embrace.android.gradle.plugin.gradle.GradleCompatibilityHelper
import io.embrace.android.gradle.plugin.instrumentation.AsmTaskRegistration
import io.embrace.android.gradle.plugin.instrumentation.config.model.VariantConfig
import io.embrace.android.gradle.plugin.model.AndroidCompactedVariantData
import io.embrace.android.gradle.plugin.tasks.il2cpp.Il2CppUploadTaskRegistration
import io.embrace.android.gradle.plugin.tasks.il2cpp.UnitySymbolFilesManager
import io.embrace.android.gradle.plugin.tasks.ndk.NdkUploadTasksRegistration
import io.embrace.android.gradle.plugin.tasks.r8.JvmMappingUploadTaskRegistration
import io.embrace.android.gradle.plugin.tasks.reactnative.GenerateRnSourcemapTaskRegistration
import org.gradle.api.Project
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Provider

/**
 * Contains the logic for configuring tasks for each variant.
 */
class TaskRegistrar(
    private val project: Project,
    private val behavior: PluginBehavior,
    private val embraceVariantConfigurationBuilder: EmbraceVariantConfigurationBuilder,
    private val variantConfigurationsListProperty: ListProperty<VariantConfig>,
    private val agpWrapper: AgpWrapper,
) {

    /**
     * It is in charge of looping through each variant and configure each task.
     * Each subclass will have its proper looping and handling of variant, since this is unique to each approach.
     */
    fun registerTasks() {
        project.extensions.getByType(AndroidComponentsExtension::class.java)
            .onVariants { variant: Variant ->
                onVariant(AndroidCompactedVariantData.from(variant), variant)
            }
    }

    private fun onVariant(variant: AndroidCompactedVariantData, ref: Variant) {
        project.installDependenciesForVariant(variant.name, behavior)

        setupVariantConfigurationListProperty(variant, variantConfigurationsListProperty)

        val params = RegistrationParams(
            project,
            ref,
            variant,
            variantConfigurationsListProperty,
            behavior,
        )

        AsmTaskRegistration().register(params)

        if (behavior.isPluginDisabledForVariant(variant.name) || !shouldRegisterUploadTasks(variant, variantConfigurationsListProperty)) {
            return
        } else {
            registerUploadTasks(params, variant)
        }
    }

    private fun registerUploadTasks(params: RegistrationParams, variant: AndroidCompactedVariantData) {
        JvmMappingUploadTaskRegistration().register(params)
        if (behavior.isReactNativeProject) {
            GenerateRnSourcemapTaskRegistration().register(params)
        }
        val variantConfig = variantConfigurationsListProperty.get().first { it.variantName == variant.name }
        val unitySymbolsDirProvider = getUnitySymbolsDirProvider(variantConfig)
        val projectType = getProjectType(unitySymbolsDirProvider, agpWrapper, variantConfig.variantName, project)
        NdkUploadTasksRegistration(behavior, unitySymbolsDirProvider, projectType, variantConfig).register(params)
        if (behavior.isIl2CppMappingFilesUploadEnabled) {
            Il2CppUploadTaskRegistration().register(params)
        }
    }

    private fun shouldRegisterUploadTasks(
        variant: AndroidCompactedVariantData,
        variantConfigurationsListProperty: ListProperty<VariantConfig>,
    ): Boolean {
        return if (behavior.isUploadMappingFilesDisabled) {
            false
        } else {
            val embraceConfig = variantConfigurationsListProperty.get().first { it.variantName == variant.name }.embraceConfig
            if (embraceConfig?.apiToken.isNullOrEmpty()) {
                false
            } else if (embraceConfig?.appId.isNullOrEmpty()) {
                false
            } else {
                true
            }
        }
    }

    private fun getUnitySymbolsDirProvider(variantConfig: VariantConfig): Provider<UnitySymbolsDir> = project.provider {
        val unityConfig = variantConfig.embraceConfig?.unityConfig
        val realProject = project.parent ?: project
        UnitySymbolFilesManager.of().getSymbolsDir(
            realProject.layout.projectDirectory,
            project.layout.projectDirectory,
            unityConfig
        )
    }

    private fun getProjectType(
        unitySymbolsDirProvider: Provider<UnitySymbolsDir>,
        agpWrapper: AgpWrapper,
        variantName: String,
        project: Project,
    ) = ProjectTypeVerifier.getProjectType(
        unitySymbolsDirProvider,
        agpWrapper,
        behavior,
        variantName,
        project
    )

    private fun setupVariantConfigurationListProperty(
        androidCompactedVariantData: AndroidCompactedVariantData,
        variantConfigurationsListProperty: ListProperty<VariantConfig>,
    ) {
        val embraceVariantConfiguration = embraceVariantConfigurationBuilder.buildVariantConfiguration(androidCompactedVariantData)
        val fullVariantConfiguration = embraceVariantConfiguration.map {
            VariantConfig.from(it, androidCompactedVariantData)
        }.orElse(
            VariantConfig.from(null, androidCompactedVariantData)
        )

        // let's add configuration for current variant to our property
        GradleCompatibilityHelper.add(variantConfigurationsListProperty, fullVariantConfiguration)
    }
}

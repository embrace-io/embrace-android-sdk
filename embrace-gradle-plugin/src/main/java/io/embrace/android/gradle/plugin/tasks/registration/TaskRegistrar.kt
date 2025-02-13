package io.embrace.android.gradle.plugin.tasks.registration

import com.android.build.api.variant.AndroidComponentsExtension
import com.android.build.api.variant.Variant
import io.embrace.android.gradle.plugin.config.PluginBehavior
import io.embrace.android.gradle.plugin.config.variant.EmbraceVariantConfigurationBuilder
import io.embrace.android.gradle.plugin.dependency.installDependenciesForVariant
import io.embrace.android.gradle.plugin.extension.EmbraceExtensionInternal
import io.embrace.android.gradle.plugin.extension.EmbraceExtensionInternalSource
import io.embrace.android.gradle.plugin.instrumentation.config.model.VariantConfig
import io.embrace.android.gradle.plugin.model.AndroidCompactedVariantData
import io.embrace.android.gradle.plugin.network.NetworkService
import io.embrace.android.gradle.plugin.tasks.il2cpp.Il2CppUploadTaskRegistration
import io.embrace.android.gradle.plugin.tasks.ndk.NdkUploadTaskRegistration
import io.embrace.android.gradle.plugin.tasks.r8.JvmMappingUploadTaskRegistration
import io.embrace.android.gradle.plugin.tasks.reactnative.EmbraceRnSourcemapGeneratorTaskRegistration
import org.gradle.api.Project
import org.gradle.api.provider.ListProperty

/**
 * Contains the logic for configuring tasks for each variant.
 */
class TaskRegistrar(
    private val project: Project,
    private val behavior: PluginBehavior,
    private val embraceVariantConfigurationBuilder: EmbraceVariantConfigurationBuilder,
    private val variantConfigurationsListProperty: ListProperty<VariantConfig>,
    private val networkService: NetworkService,
) {

    private val embraceExtensionInternal: EmbraceExtensionInternal = checkNotNull(
        project.extensions.findByType(EmbraceExtensionInternal::class.java)
    )

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

        EmbraceExtensionInternalSource().setupExtension(
            project,
            behavior,
            variant,
            embraceVariantConfigurationBuilder,
            variantConfigurationsListProperty
        )

        if (behavior.isPluginDisabledForVariant(variant.name) || !shouldRegisterUploadTasks(variant)) {
            return
        } else {
            val params = RegistrationParams(
                project,
                ref,
                variant,
                networkService,
                embraceExtensionInternal,
                behavior.baseUrl,
            )
            registerTasks(params)
        }
    }

    private fun registerTasks(params: RegistrationParams) {
        JvmMappingUploadTaskRegistration().register(params)
        if (behavior.isReactNativeProject) {
            val taskRegistration = EmbraceRnSourcemapGeneratorTaskRegistration()
            taskRegistration.register(params)
        }
        NdkUploadTaskRegistration(behavior).register(params)
        if (behavior.isIl2CppMappingFilesUploadEnabled) {
            Il2CppUploadTaskRegistration().register(params)
        }
    }

    private fun shouldRegisterUploadTasks(variant: AndroidCompactedVariantData): Boolean {
        return if (behavior.isUploadMappingFilesDisabled) {
            false
        } else {
            val variantExtension = embraceExtensionInternal.variants.getByName(variant.name)
            if (variantExtension.apiToken.orNull.isNullOrEmpty()) {
                false
            } else if (variantExtension.appId.orNull.isNullOrEmpty()) {
                false
            } else {
                true
            }
        }
    }
}

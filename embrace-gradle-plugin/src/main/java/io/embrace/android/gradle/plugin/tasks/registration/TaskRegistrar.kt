package io.embrace.android.gradle.plugin.tasks.registration

import com.android.build.api.variant.AndroidComponentsExtension
import com.android.build.api.variant.Variant
import io.embrace.android.gradle.plugin.EmbraceLogger
import io.embrace.android.gradle.plugin.agp.AgpWrapper
import io.embrace.android.gradle.plugin.buildreporter.BuildTelemetryService
import io.embrace.android.gradle.plugin.config.PluginBehavior
import io.embrace.android.gradle.plugin.config.variant.EmbraceVariantConfigurationBuilder
import io.embrace.android.gradle.plugin.dependency.installDependenciesForVariant
import io.embrace.android.gradle.plugin.instrumentation.AsmTaskRegistration
import io.embrace.android.gradle.plugin.instrumentation.config.model.VariantConfig
import io.embrace.android.gradle.plugin.model.AndroidCompactedVariantData
import io.embrace.android.gradle.plugin.tasks.il2cpp.Il2CppUploadTaskRegistration
import io.embrace.android.gradle.plugin.tasks.ndk.NdkUploadTasksRegistration
import io.embrace.android.gradle.plugin.tasks.r8.JvmMappingUploadTaskRegistration
import io.embrace.android.gradle.plugin.tasks.reactnative.GenerateRnSourcemapTaskRegistration
import io.embrace.android.gradle.plugin.util.BuildIdValueSource
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

    private val logger = EmbraceLogger(TaskRegistrar::class.java)

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

    /**
     * Registers React Native specific tasks for all variants.
     * This method is called when the React Native plugin is detected.
     */
    fun registerReactNativeTasks() {
        project.extensions.getByType(AndroidComponentsExtension::class.java)
            .onVariants { variant: Variant ->
                onReactNativeVariant(AndroidCompactedVariantData.from(variant), variant)
            }
    }

    private fun onVariant(variant: AndroidCompactedVariantData, ref: Variant) {
        val buildIdProvider: Provider<String> = project.providers.of(BuildIdValueSource::class.java) {
            it.parameters.getVariantName().set(variant.name)
        }

        project.installDependenciesForVariant(variant.name, behavior)

        setupVariantConfigurationListProperty(variant, variantConfigurationsListProperty, buildIdProvider)

        val params = createRegistrationParams(variant, ref, buildIdProvider)

        AsmTaskRegistration().register(params)

        if (shouldSkipUploadTasks(variant)) {
            logger.info("Skipping upload tasks for variant: ${variant.name}")
            return
        } else {
            registerUploadTasks(params, variant)
        }
    }

    private fun onReactNativeVariant(variant: AndroidCompactedVariantData, ref: Variant) {
        if (shouldSkipUploadTasks(variant)) {
            logger.info("Skipping RN upload task for variant: ${variant.name}")
            return
        }

        val buildIdProvider: Provider<String> = project.providers.of(BuildIdValueSource::class.java) {
            it.parameters.getVariantName().set(variant.name)
        }

        GenerateRnSourcemapTaskRegistration().register(
            createRegistrationParams(variant, ref, buildIdProvider)
        )
    }

    private fun shouldSkipUploadTasks(variant: AndroidCompactedVariantData): Boolean {
        return variant.isBuildTypeDebuggable || behavior.isPluginDisabledForVariant(variant.name) ||
            !shouldRegisterUploadTasks(variant, variantConfigurationsListProperty)
    }

    private fun createRegistrationParams(
        variant: AndroidCompactedVariantData,
        ref: Variant,
        buildIdProvider: Provider<String>,
    ): RegistrationParams {
        return RegistrationParams(
            project,
            ref,
            variant,
            variantConfigurationsListProperty,
            behavior,
            buildIdProvider,
        )
    }

    private fun registerUploadTasks(params: RegistrationParams, variant: AndroidCompactedVariantData) {
        JvmMappingUploadTaskRegistration().register(params)
        val variantConfig = variantConfigurationsListProperty.get().first { it.variantName == variant.name }
        NdkUploadTasksRegistration(behavior, variantConfig).register(params)
        if (behavior.isIl2CppMappingFilesUploadEnabled) {
            Il2CppUploadTaskRegistration().register(params)
        }
        BuildTelemetryService.register(
            project,
            variantConfigurationsListProperty,
            behavior,
            agpWrapper
        )
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

    private fun setupVariantConfigurationListProperty(
        androidCompactedVariantData: AndroidCompactedVariantData,
        variantConfigurationsListProperty: ListProperty<VariantConfig>,
        buildIdProvider: Provider<String>,
    ) {
        val embraceVariantConfiguration = embraceVariantConfigurationBuilder.buildVariantConfiguration(androidCompactedVariantData)
        // buildIdProvider is ValueSource-backed: Gradle re-evaluates it on every build even when
        // the configuration cache is active, ensuring a fresh build ID is used each time.
        val fullVariantConfiguration = buildIdProvider.flatMap { buildId ->
            embraceVariantConfiguration
                .map { VariantConfig.from(it, androidCompactedVariantData, buildId) }
                .orElse(VariantConfig.from(null, androidCompactedVariantData, buildId))
        }

        // let's add configuration for current variant to our property
        variantConfigurationsListProperty.add(fullVariantConfiguration)
    }
}

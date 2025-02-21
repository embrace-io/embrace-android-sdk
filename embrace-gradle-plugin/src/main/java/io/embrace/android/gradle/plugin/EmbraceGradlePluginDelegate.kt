package io.embrace.android.gradle.plugin

import io.embrace.android.gradle.plugin.agp.AgpUtils
import io.embrace.android.gradle.plugin.agp.AgpVersion
import io.embrace.android.gradle.plugin.agp.AgpWrapper
import io.embrace.android.gradle.plugin.agp.AgpWrapperImpl
import io.embrace.android.gradle.plugin.buildreporter.BuildTelemetryService
import io.embrace.android.gradle.plugin.config.PluginBehaviorImpl
import io.embrace.android.gradle.plugin.config.variant.EmbraceVariantConfigurationBuilder
import io.embrace.android.gradle.plugin.extension.EXTENSION_EMBRACE_INTERNAL
import io.embrace.android.gradle.plugin.extension.EmbraceExtensionInternal
import io.embrace.android.gradle.plugin.gradle.getProperty
import io.embrace.android.gradle.plugin.instrumentation.config.model.VariantConfig
import io.embrace.android.gradle.plugin.instrumentation.registerAsmTasks
import io.embrace.android.gradle.plugin.network.OkHttpNetworkService
import io.embrace.android.gradle.plugin.tasks.registration.TaskRegistrar
import io.embrace.android.gradle.swazzler.plugin.extension.SwazzlerExtension
import org.gradle.api.Project
import org.gradle.api.provider.ListProperty

/**
 * Entry point for Android-specific code in the Embrace gradle plugin.
 */
class EmbraceGradlePluginDelegate {

    private val logger: Logger<EmbraceGradlePluginDelegate> = Logger(EmbraceGradlePluginDelegate::class.java)

    fun onAndroidPluginApplied(
        project: Project,
        variantConfigurationsListProperty: ListProperty<VariantConfig>,
        extension: SwazzlerExtension,
    ) {
        val behavior = PluginBehaviorImpl(project, extension)
        Logger.setPluginLogLevel(behavior.logLevel)
        val agpExtension = AgpWrapperImpl(project)
        val networkService = OkHttpNetworkService(behavior.baseUrl)

        project.extensions.create(
            EXTENSION_EMBRACE_INTERNAL,
            EmbraceExtensionInternal::class.java,
            project.objects
        )

        BuildTelemetryService.register(
            project,
            variantConfigurationsListProperty,
            behavior,
        )

        // bytecode instrumentation must be registered before project evaluation
        registerAsmTasks(project, behavior, variantConfigurationsListProperty)

        val embraceVariantConfigurationBuilder =
            EmbraceVariantConfigurationBuilder(
                project.layout.projectDirectory,
                project.providers
            )

        val taskRegistrar = TaskRegistrar(
            project,
            behavior,
            embraceVariantConfigurationBuilder,
            variantConfigurationsListProperty,
            networkService,
        )

        taskRegistrar.registerTasks()

        project.afterEvaluate { evaluatedProject ->
            onProjectEvaluated(evaluatedProject, agpExtension)
        }
    }

    /**
     * It gets called once this project has been evaluated.
     */
    private fun onProjectEvaluated(evaluatedProject: Project, agpWrapper: AgpWrapper) {
        verifyDesugaringForOldDevices(agpWrapper)
        verifySemConvWorkaround(evaluatedProject, agpWrapper)
    }

    private fun verifyDesugaringForOldDevices(agpWrapper: AgpWrapper) {
        var enableDesugaring = false
        try {
            val shouldEnableDesugaring = when (val minSdk = agpWrapper.minSdk) {
                null -> true
                else -> AgpUtils.isDesugaringRequired(minSdk)
            }
            if (shouldEnableDesugaring) {
                enableDesugaring = !agpWrapper.isCoreLibraryDesugaringEnabled
            }
        } catch (e: Throwable) {
            logger.info(
                "There was an exception while checking if desugaring is required. " +
                    "We will consider desugaring is not required. Build will continue normally."
            )
        }

        if (enableDesugaring) {
            error(
                "Desugaring must be enabled when minSdk is < 24, " +
                    "otherwise devices on Android API version < 24 will fail at runtime.\n" +
                    "You can enable desugaring by adding the following:\n" +
                    "compileOptions\n" +
                    "{\n" +
                    "  coreLibraryDesugaringEnabled = true\n" +
                    "}\n" +
                    "dependencies {\n" +
                    "    coreLibraryDesugaring 'com.android.tools:desugar_jdk_libs:2.0.3'\n" +
                    "}\n" +
                    "in your app\'s module build.gradle file. Use a newer version of the desugaring library if required.\n" +
                    "You can find more info at: \n" +
                    "https://developer.android.com/studio/write/java8-support#library-desugaring"
            )
        }
    }

    private fun verifySemConvWorkaround(project: Project, agpWrapper: AgpWrapper) {
        val minSdk = agpWrapper.minSdk ?: return

        if (minSdk < 24) {
            if (agpWrapper.version <= AgpVersion.AGP_8_3_0 ||
                project.getProperty("android.useFullClasspathForDexingTransform").orNull != "true"
            ) {
                error(
                    "To use the Embrace SDK when your minSdk is lower than 24 " +
                        "you must use AGP 8.3.0+ and add android.useFullClasspathForDexingTransform=true to " +
                        "gradle.properties.\nAlternatively you can set your minSdk to 24 or higher.\n" +
                        "This avoids a desugaring bug in old AGP versions that will lead to runtime crashes on old devices.\n" +
                        "For the full context for this workaround, please see the following issue:" +
                        " https://issuetracker.google.com/issues/230454566#comment18"
                )
            }
        }
    }
}

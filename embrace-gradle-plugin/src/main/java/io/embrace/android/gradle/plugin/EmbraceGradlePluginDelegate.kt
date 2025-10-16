package io.embrace.android.gradle.plugin

import io.embrace.android.gradle.plugin.agp.AgpUtils
import io.embrace.android.gradle.plugin.agp.AgpVersion
import io.embrace.android.gradle.plugin.agp.AgpWrapper
import io.embrace.android.gradle.plugin.agp.AgpWrapperImpl
import io.embrace.android.gradle.plugin.api.EmbraceExtension
import io.embrace.android.gradle.plugin.config.PluginBehaviorImpl
import io.embrace.android.gradle.plugin.config.variant.EmbraceVariantConfigurationBuilder
import io.embrace.android.gradle.plugin.gradle.getProperty
import io.embrace.android.gradle.plugin.instrumentation.config.model.VariantConfig
import io.embrace.android.gradle.plugin.tasks.registration.TaskRegistrar
import org.gradle.api.Project
import org.gradle.api.provider.ListProperty

/**
 * Entry point for Android-specific code in the Embrace gradle plugin.
 */
class EmbraceGradlePluginDelegate {

    private val logger = EmbraceLogger(EmbraceGradlePluginDelegate::class.java)

    fun onAndroidPluginApplied(
        project: Project,
        variantConfigurationsListProperty: ListProperty<VariantConfig>,
        embrace: EmbraceExtension,
    ) {
        val agpWrapper = AgpWrapperImpl(project)
        validateMinAgpVersion(agpWrapper)

        val behavior = PluginBehaviorImpl(project, embrace)

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
            agpWrapper
        )

        taskRegistrar.registerTasks()

        // Register React Native specific tasks when React Native plugin is also applied
        project.pluginManager.withPlugin("com.facebook.react") {
            taskRegistrar.registerReactNativeTasks()
        }

        project.afterEvaluate { evaluatedProject ->
            onProjectEvaluated(evaluatedProject, agpWrapper)
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
                "Desugaring must be enabled when minSdk is < 26, " +
                    "otherwise devices on Android API version < 26 will fail at runtime.\n" +
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

        if (minSdk < 26) {
            if (agpWrapper.version < AgpVersion.AGP_8_3_0 ||
                project.getProperty("android.useFullClasspathForDexingTransform").orNull != "true"
            ) {
                error(
                    "To use the Embrace SDK when your minSdk is lower than 26 " +
                        "you must use AGP 8.3.0+ and add android.useFullClasspathForDexingTransform=true to " +
                        "gradle.properties.\nAlternatively you can set your minSdk to 26 or higher.\n" +
                        "This avoids a desugaring bug in old AGP versions that will lead to runtime crashes on old devices.\n" +
                        "For the full context for this workaround, please see the following issue:" +
                        " https://issuetracker.google.com/issues/230454566#comment18"
                )
            }
        }
    }

    private fun validateMinAgpVersion(agpWrapper: AgpWrapper) {
        if (agpWrapper.version < AgpVersion.MIN_VERSION) {
            error("Embrace requires AGP version ${AgpVersion.MIN_VERSION} or higher. Please update your AGP version.")
        }
    }
}

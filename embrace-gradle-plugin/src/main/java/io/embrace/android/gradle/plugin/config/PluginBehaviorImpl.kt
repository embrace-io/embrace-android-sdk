package io.embrace.android.gradle.plugin.config

import io.embrace.android.gradle.plugin.Logger
import io.embrace.android.gradle.swazzler.plugin.extension.SwazzlerExtension
import org.gradle.api.Project
import org.gradle.api.logging.LogLevel
import java.io.File

class PluginBehaviorImpl(
    private val project: Project,
    private val extension: SwazzlerExtension,
) : PluginBehavior {

    override val instrumentation: InstrumentationBehavior by lazy {
        InstrumentationBehaviorImpl(project, extension)
    }

    override val logLevel: LogLevel? by lazy {
        Logger.getSupportedLogLevel(project.getProperty(EMBRACE_LOG_LEVEL))
    }

    override val isTelemetryDisabled: Boolean by lazy {
        project.getBoolProperty(EMBRACE_DISABLE_COLLECT_BUILD_DATA)
    }

    override val isUnityEdmEnabled: Boolean by lazy {
        project.getBoolProperty(EMBRACE_UNITY_EXTERNAL_DEPENDENCY_MANAGER)
    }

    override val isIl2CppMappingFilesUploadEnabled: Boolean by lazy {
        project.getBoolProperty(EMBRACE_UPLOAD_IL2CPP_MAPPING_FILES)
    }

    override val isUploadMappingFilesDisabled: Boolean by lazy {
        project.getBoolProperty(EMBRACE_DISABLE_MAPPING_FILE_UPLOAD)
    }

    override val failBuildOnUploadErrors: Boolean by lazy {
        project.getBoolProperty(EMBRACE_FAIL_BUILD_ON_UPLOAD_ERRORS)
    }

    override val baseUrl: String by lazy {
        val prop = project.getProperty(EMBRACE_BASE_URL)
            ?: return@lazy DEFAULT_SYMBOL_STORE_HOST_URL
        if (prop.startsWith("http://") || prop.startsWith("https://")) {
            prop
        } else {
            "https://$prop"
        }
    }

    override val isReactNativeProject: Boolean by lazy {
        val rootFile = project.layout.projectDirectory.asFile.parentFile?.parentFile
        if (rootFile != null) {
            val nodeModules = File("${rootFile.path}/node_modules")
            val nodeModulesEmbrace = File("${nodeModules.path}/react-native")
            nodeModulesEmbrace.exists()
        } else {
            false
        }
    }

    override val autoAddEmbraceDependencies: Boolean by lazy {
        !extension.disableDependencyInjection.get() && !isUnityEdmEnabled
    }

    override val autoAddEmbraceComposeDependency: Boolean by lazy {
        !extension.disableComposeDependencyInjection.get()
    }

    @Suppress("DEPRECATION")
    override val customSymbolsDirectory: String? by lazy {
        extension.customSymbolsDirectory.get()
    }

    override fun isInstrumentationDisabledForVariant(variantName: String): Boolean {
        val variant = findVariant(variantName)
        return !variant.enabled
    }

    override fun isPluginDisabledForVariant(variantName: String): Boolean {
        val variant = findVariant(variantName)
        return variant.swazzlerOff
    }

    private fun findVariant(variantName: String): SwazzlerExtension.Variant {
        return SwazzlerExtension.Variant(variantName).also {
            extension.variantFilter?.execute(it)
        }
    }
}

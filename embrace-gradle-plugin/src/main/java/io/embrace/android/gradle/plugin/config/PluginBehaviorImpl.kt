package io.embrace.android.gradle.plugin.config

import io.embrace.android.gradle.plugin.api.EmbraceExtension
import io.embrace.android.gradle.swazzler.plugin.extension.SwazzlerExtension
import org.gradle.api.Project
import java.io.File

@Suppress("DEPRECATION")
class PluginBehaviorImpl(
    private val project: Project,
    private val extension: SwazzlerExtension,
    private val embrace: EmbraceExtension,
) : PluginBehavior {

    override val instrumentation: InstrumentationBehavior by lazy {
        InstrumentationBehaviorImpl(extension)
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
        project.getProperty(EMBRACE_FAIL_BUILD_ON_UPLOAD_ERRORS) != "false"
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
        val userValue = embrace.autoAddEmbraceDependencies.orNull ?: extension.disableDependencyInjection.orNull?.not() ?: true
        userValue && !isUnityEdmEnabled
    }

    override val autoAddEmbraceComposeDependency: Boolean by lazy {
        !extension.disableComposeDependencyInjection.get()
    }

    @Suppress("DEPRECATION")
    override val customSymbolsDirectory: String? by lazy {
        extension.customSymbolsDirectory.orNull
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

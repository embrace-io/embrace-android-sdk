package io.embrace.android.gradle.plugin.config

import io.embrace.android.gradle.plugin.api.EmbraceExtension
import io.embrace.android.gradle.plugin.util.getBoolProperty
import io.embrace.android.gradle.plugin.util.getProperty
import org.gradle.api.Project
import org.gradle.api.provider.Provider

class PluginBehaviorImpl(
    private val project: Project,
    private val embrace: EmbraceExtension,
) : PluginBehavior {

    override val instrumentation: InstrumentationBehavior by lazy {
        InstrumentationBehaviorImpl(embrace)
    }

    override val isTelemetryDisabled: Provider<Boolean> by lazy {
        project.provider {
            embrace.telemetryEnabled.orNull?.not() ?: project.getBoolProperty(EMBRACE_DISABLE_COLLECT_BUILD_DATA)
        }
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

    override val failBuildOnUploadErrors: Provider<Boolean> by lazy {
        project.provider {
            embrace.failBuildOnUploadErrors.get()
        }
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

    @Deprecated(
        "This will be removed in a future release." +
            " Add embrace dependencies to the classpath manually instead."
    )
    override val autoAddEmbraceDependencies: Boolean by lazy {
        val userValue = embrace.autoAddEmbraceDependencies.orNull ?: true
        userValue && !isUnityEdmEnabled
    }

    override val autoAddEmbraceComposeDependency: Boolean by lazy {
        embrace.autoAddEmbraceComposeClickDependency.orNull ?: false
    }

    override val customSymbolsDirectory: String? by lazy {
        embrace.customSymbolsDirectory.orNull ?: ""
    }

    override fun isInstrumentationDisabledForVariant(variantName: String): Boolean {
        return !findBuildVariant(variantName).bytecodeInstrumentationEnabled
    }

    override fun isPluginDisabledForVariant(variantName: String): Boolean {
        return !findBuildVariant(variantName).pluginEnabled
    }

    private fun findBuildVariant(variantName: String): EmbraceExtension.BuildVariant {
        return EmbraceExtension.BuildVariant(variantName).also {
            embrace.buildVariantFilter.execute(it)
        }
    }
}

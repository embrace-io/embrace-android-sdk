package io.embrace.android.gradle.plugin.config

import org.gradle.api.logging.LogLevel

interface PluginBehavior {

    /**
     * The log level, set via `embrace.logLevel`
     */
    val logLevel: LogLevel?

    /**
     * Whether telemetry can be captured for this build, set via `embrace.disableCollectBuildData`.
     */
    val isTelemetryDisabled: Boolean

    /**
     * Whether Unity EDM is enabled, set via `embrace.externalDependencyManager`
     */
    val isUnityEdmEnabled: Boolean

    /**
     * Whether IL2CPP (Unity) mapping file upload is enabled, set via `embrace.uploadIl2CppMappingFiles`
     */
    val isIl2CppMappingFilesUploadEnabled: Boolean

    /**
     * Whether JVM mapping file upload is enabled, set via `embrace.disableMappingFileUpload`
     */
    val isUploadMappingFilesDisabled: Boolean

    /**
     * The base URL for the Embrace API, set via `embrace.baseUrl`
     */
    val baseUrl: String

    /**
     * Whether the project uses React Native or not
     */
    val isReactNativeProject: Boolean

    /**
     * The behavior for instrumenting bytecode
     */
    val instrumentation: InstrumentationBehavior

    /**
     * Whether the project should automatically add embrace dependencies to the classpath,
     * set via `swazzler.disableDependencyInjection`
     */
    val autoAddEmbraceDependencies: Boolean

    /**
     * Whether the project should automatically add the embrace compose dependency to the classpath,
     * set via `swazzler.disableComposeDependencyInjection`
     */
    val autoAddEmbraceComposeDependency: Boolean

    /**
     * A custom directory containing SO files, set via `swazzler.customSymbolsDirectory`
     */
    val customSymbolsDirectory: String?

    /**
     * Whether bytecode instrumentation is disabled for this variant, set via `swazzler.variantFilter`.
     */
    fun isInstrumentationDisabledForVariant(variantName: String): Boolean

    /**
     * Whether the plugin is disabled for this variant, set via `swazzler.variantFilter`.
     */
    fun isPluginDisabledForVariant(variantName: String): Boolean
}

const val EMBRACE_BASE_URL = "embrace.baseUrl"
const val EMBRACE_LOG_LEVEL = "embrace.logLevel"
const val EMBRACE_INSTRUMENTATION_SCOPE = "embrace.instrumentationScope"
const val EMBRACE_DISABLE_COLLECT_BUILD_DATA = "embrace.disableCollectBuildData"
const val EMBRACE_UPLOAD_IL2CPP_MAPPING_FILES = "embrace.uploadIl2CppMappingFiles"
const val EMBRACE_DISABLE_MAPPING_FILE_UPLOAD = "embrace.disableMappingFileUpload"
const val EMBRACE_UNITY_EXTERNAL_DEPENDENCY_MANAGER = "embrace.externalDependencyManager"
const val DEFAULT_SYMBOL_STORE_HOST_URL = "https://dsym-store.emb-api.com"

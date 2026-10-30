package io.embrace.bytecode.config

import io.embrace.bytecode.config.arch.sdk.createBaseUrlConfigInstrumentation
import io.embrace.bytecode.config.arch.sdk.createEnabledFeatureConfigInstrumentation
import io.embrace.bytecode.config.arch.sdk.createNetworkCaptureConfigInstrumentation
import io.embrace.bytecode.config.arch.sdk.createProjectConfigInstrumentation
import io.embrace.bytecode.config.arch.sdk.createRedactionConfigInstrumentation
import io.embrace.bytecode.config.arch.sdk.createSharedObjectFilesMapInstrumentation
import io.embrace.bytecode.config.local.LocalConfigClassVisitor
import io.embrace.bytecode.config.model.VariantConfig
import io.embrace.bytecode.config.model.VariantOutputInfo
import io.embrace.bytecode.config.visitor.ConfigInstrumentationClassVisitor
import org.objectweb.asm.ClassVisitor

object ConfigClassVisitorFactory {
    private const val CONFIG_PACKAGE = "io.embrace.android.embracesdk.internal.config.instrumented."

    private enum class ConfigClassType {
        BaseUrlConfig,
        EnabledFeatureConfig,
        NetworkCaptureConfig,
        ProjectConfig,
        RedactionConfig,
        Base64SharedObjectFilesMap,
        ;

        val className = "$CONFIG_PACKAGE${name}Impl"

        fun createClassVisitor(
            cfg: VariantConfig,
            encodedSharedObjectFilesMap: String?,
            reactNativeBundleId: String?,
            variantOutputInfo: VariantOutputInfo,
            api: Int,
            cv: ClassVisitor?,
        ): ClassVisitor {
            val instrumentation = when (this) {
                BaseUrlConfig -> createBaseUrlConfigInstrumentation(cfg)
                EnabledFeatureConfig -> createEnabledFeatureConfigInstrumentation(cfg)
                NetworkCaptureConfig -> createNetworkCaptureConfigInstrumentation(cfg)
                ProjectConfig -> createProjectConfigInstrumentation(cfg, reactNativeBundleId, variantOutputInfo)
                RedactionConfig -> createRedactionConfigInstrumentation(cfg)
                Base64SharedObjectFilesMap -> createSharedObjectFilesMapInstrumentation(encodedSharedObjectFilesMap)
            }
            return ConfigInstrumentationClassVisitor(instrumentation, api, cv)
        }
    }

    /**
     * Whether [className] is a config class in the SDK that [createClassVisitor] instruments.
     */
    fun isConfigClass(className: String): Boolean = className.startsWith(CONFIG_PACKAGE)

    /**
     * Creates a class visitor that instruments a config class in the SDK, or returns null if the
     * class is not a config class.
     */
    fun createClassVisitor(
        className: String,
        cfg: VariantConfig,
        encodedSharedObjectFilesMap: String?,
        variantOutputInfo: VariantOutputInfo,
        reactNativeBundleId: String?,
        api: Int,
        cv: ClassVisitor?,
    ): ClassVisitor? {
        val type = ConfigClassType.entries.singleOrNull { it.className == className }
        return type?.createClassVisitor(cfg, encodedSharedObjectFilesMap, reactNativeBundleId, variantOutputInfo, api, cv)
            ?: if (isConfigClass(className)) {
                LocalConfigClassVisitor(cfg.embraceConfig?.json, api, cv)
            } else {
                null
            }
    }
}

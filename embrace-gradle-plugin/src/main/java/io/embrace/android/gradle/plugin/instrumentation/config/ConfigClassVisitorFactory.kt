package io.embrace.android.gradle.plugin.instrumentation.config

import io.embrace.android.gradle.plugin.instrumentation.config.arch.sdk.createBaseUrlConfigInstrumentation
import io.embrace.android.gradle.plugin.instrumentation.config.arch.sdk.createEnabledFeatureConfigInstrumentation
import io.embrace.android.gradle.plugin.instrumentation.config.arch.sdk.createNetworkCaptureConfigInstrumentation
import io.embrace.android.gradle.plugin.instrumentation.config.arch.sdk.createProjectConfigInstrumentation
import io.embrace.android.gradle.plugin.instrumentation.config.arch.sdk.createRedactionConfigInstrumentation
import io.embrace.android.gradle.plugin.instrumentation.config.arch.sdk.createSessionConfigInstrumentation
import io.embrace.android.gradle.plugin.instrumentation.config.model.VariantConfig
import io.embrace.android.gradle.plugin.instrumentation.config.visitor.ConfigInstrumentationClassVisitor
import org.objectweb.asm.ClassVisitor

object ConfigClassVisitorFactory {
    private enum class ConfigClassType {
        BaseUrlConfig,
        EnabledFeatureConfig,
        NetworkCaptureConfig,
        ProjectConfig,
        RedactionConfig,
        SessionConfig;

        val className = "io.embrace.android.embracesdk.internal.config.instrumented.${name}Impl"

        fun createClassVisitor(
            cfg: VariantConfig,
            api: Int,
            cv: ClassVisitor?
        ): ClassVisitor {
            val instrumentation = when (this) {
                BaseUrlConfig -> createBaseUrlConfigInstrumentation(cfg)
                EnabledFeatureConfig -> createEnabledFeatureConfigInstrumentation(cfg)
                NetworkCaptureConfig -> createNetworkCaptureConfigInstrumentation(cfg)
                ProjectConfig -> createProjectConfigInstrumentation(cfg)
                RedactionConfig -> createRedactionConfigInstrumentation(cfg)
                SessionConfig -> createSessionConfigInstrumentation(cfg)
            }
            return ConfigInstrumentationClassVisitor(instrumentation, api, cv)
        }
    }

    /**
     * Creates a class visitor that instruments a config class in the SDK, or returns null if the
     * class is not a config class.
     */
    fun createClassVisitor(
        className: String,
        cfg: VariantConfig,
        api: Int,
        cv: ClassVisitor?
    ): ClassVisitor? {
        val type = ConfigClassType.values().singleOrNull { it.className == className }
        return type?.createClassVisitor(cfg, api, cv)
    }
}

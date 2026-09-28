package io.embrace.android.gradle.plugin.instrumentation.config

import io.embrace.android.gradle.plugin.instrumentation.config.arch.sdk.createConfigInstrumentation
import io.embrace.android.gradle.plugin.instrumentation.config.model.VariantConfig
import io.embrace.android.gradle.plugin.instrumentation.config.visitor.ConfigInstrumentationClassVisitor
import io.embrace.android.gradle.plugin.model.VariantOutputInfo
import org.objectweb.asm.ClassVisitor

object ConfigClassVisitorFactory {

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
        val instrumentation = createConfigInstrumentation(
            className = className,
            cfg = cfg,
            encodedSharedObjectFilesMap = encodedSharedObjectFilesMap,
            reactNativeBundleId = reactNativeBundleId,
            variantOutputInfo = variantOutputInfo,
        ) ?: return null
        return ConfigInstrumentationClassVisitor(instrumentation, api, cv)
    }
}

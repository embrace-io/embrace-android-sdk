package io.embrace.android.gradle.plugin.instrumentation

import io.embrace.android.gradle.plugin.instrumentation.config.ConfigClassVisitorFactory
import io.embrace.android.gradle.plugin.instrumentation.config.model.VariantConfig
import io.embrace.android.gradle.plugin.instrumentation.json.readBytecodeInstrumentationFeatures
import io.embrace.android.gradle.plugin.instrumentation.strategy.ClassInfo
import io.embrace.android.gradle.plugin.model.VariantOutputInfo
import org.objectweb.asm.ClassVisitor

/**
 * Decides which classes get instrumented and builds their visitors. The suppliers are only invoked for config classes.
 */
class BytecodeInstrumenter(
    private val config: VariantConfig,
    private val variantOutputInfo: VariantOutputInfo,
    private val encodedSharedObjectFilesMap: () -> String?,
    private val reactNativeBundleId: () -> String?,
    private val flags: InstrumentationFeatureFlags,
    private val filter: ClassInstrumentationFilter,
    private val disabled: Boolean,
) {

    fun isInstrumentable(className: String): Boolean = !disabled && !filter.shouldSkip(className)

    fun createClassVisitor(classInfo: ClassInfo, api: Int, next: ClassVisitor): ClassVisitor {
        var visitor = next
        val className = classInfo.className

        // Add a visitor if this is a config class provided by the SDK.
        if (ConfigClassVisitorFactory.isConfigClass(className)) {
            ConfigClassVisitorFactory.createClassVisitor(
                className,
                config,
                encodedSharedObjectFilesMap(),
                variantOutputInfo,
                reactNativeBundleId(),
                api,
                visitor,
            )?.let {
                visitor = it
            }
        }

        val factory = VisitorFactoryImpl(api, flags)
        readBytecodeInstrumentationFeatures().forEach { feature ->
            visitor = factory.createClassVisitor(feature, classInfo, visitor)
        }
        return visitor
    }
}

package io.embrace.bytecode

import io.embrace.bytecode.config.ConfigClassVisitorFactory
import io.embrace.bytecode.config.model.VariantConfig
import io.embrace.bytecode.config.model.VariantOutputInfo
import io.embrace.bytecode.json.readBytecodeInstrumentationFeatures
import io.embrace.bytecode.strategy.ClassInfo
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

package io.embrace.android.gradle.plugin.instrumentation.config.arch

import org.objectweb.asm.MethodVisitor

/**
 * Holds data on how a SDK config class should be instrumented with configuration values.
 */
class InstrumentedConfigClass(
    private val methods: MutableList<InstrumentedConfigMethod> = mutableListOf()
) {

    fun addMethod(method: InstrumentedConfigMethod) {
        methods.add(method)
    }

    /**
     * Returns a MethodVisitor that will instrument the method if it is a target for configuration,
     * otherwise it returns the existing visitor.
     */
    fun getMethodVisitor(
        name: String,
        descriptor: String,
        api: Int,
        visitor: MethodVisitor
    ): MethodVisitor {
        val match = methods.singleOrNull {
            it.isConfigInstrumentationTarget(name, descriptor)
        } ?: return visitor
        return match.getMethodVisitor(api, visitor)
    }
}

package io.embrace.android.gradle.plugin.instrumentation.config.visitor

import io.embrace.android.gradle.plugin.instrumentation.config.arch.InstrumentedConfigClass
import org.objectweb.asm.ClassVisitor
import org.objectweb.asm.MethodVisitor

/**
 * Instruments the config classes with defaults from embrace-config.json.
 */
class ConfigInstrumentationClassVisitor(
    private val instrumentedConfigClass: InstrumentedConfigClass,
    api: Int,
    cv: ClassVisitor?
) : ClassVisitor(api, cv) {

    override fun visitMethod(
        access: Int,
        name: String,
        descriptor: String,
        signature: String?,
        exceptions: Array<out String>?
    ): MethodVisitor {
        val visitor = super.visitMethod(access, name, descriptor, signature, exceptions)
        return instrumentedConfigClass.getMethodVisitor(name, descriptor, api, visitor)
    }
}

package io.embrace.android.gradle.plugin.instrumentation.visitor

import org.objectweb.asm.ClassVisitor
import org.objectweb.asm.MethodVisitor

/**
 * Visits a class and adds [InstrumentationTargetMethodVisitor] to any methods that require bytecode instrumentation.
 */
internal class InstrumentationTargetClassVisitor(
    api: Int,
    nextClassVisitor: ClassVisitor?,
    private val targetParams: BytecodeClassInsertionParams,
    private val insertionParams: BytecodeMethodInsertionParams,
) : ClassVisitor(api, nextClassVisitor) {

    override fun visitMethod(
        access: Int,
        name: String,
        desc: String,
        signature: String?,
        exceptions: Array<String>?,
    ): MethodVisitor? {
        val nextMethodVisitor = super.visitMethod(access, name, desc, signature, exceptions)

        return if (targetParams.name == name && targetParams.descriptor == desc) {
            InstrumentationTargetMethodVisitor(
                api = api,
                methodVisitor = nextMethodVisitor,
                params = insertionParams
            )
        } else {
            nextMethodVisitor
        }
    }
}

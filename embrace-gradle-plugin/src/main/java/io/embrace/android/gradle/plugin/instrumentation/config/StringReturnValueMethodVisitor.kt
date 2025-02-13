package io.embrace.android.gradle.plugin.instrumentation.config

import org.objectweb.asm.MethodVisitor
import org.objectweb.asm.Opcodes

/**
 * Visits a string method and replaces its return value with the [replacedValue] parameter.
 */
class StringReturnValueMethodVisitor(
    val replacedValue: String,
    api: Int,
    nextVisitor: MethodVisitor
) : MethodVisitor(api, nextVisitor) {

    override fun visitInsn(opcode: Int) {
        if (opcode == Opcodes.ARETURN) {
            visitLdcInsn(replacedValue)
        }
        super.visitInsn(opcode)
    }
}

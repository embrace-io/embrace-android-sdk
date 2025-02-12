package io.embrace.android.gradle.plugin.instrumentation.config

import org.objectweb.asm.MethodVisitor
import org.objectweb.asm.Opcodes

/**
 * Visits a long method and replaces its return value with the [replacedValue] parameter.
 */
class LongReturnValueMethodVisitor(
    val replacedValue: Long,
    api: Int,
    nextVisitor: MethodVisitor
) : MethodVisitor(api, nextVisitor) {

    override fun visitInsn(opcode: Int) {
        if (opcode == Opcodes.LRETURN) {
            visitLdcInsn(replacedValue)
        }
        super.visitInsn(opcode)
    }
}

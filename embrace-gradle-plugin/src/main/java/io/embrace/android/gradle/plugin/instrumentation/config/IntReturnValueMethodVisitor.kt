package io.embrace.android.gradle.plugin.instrumentation.config

import org.objectweb.asm.MethodVisitor
import org.objectweb.asm.Opcodes

/**
 * Visits a int method and replaces its return value with the [replacedValue] parameter.
 */
class IntReturnValueMethodVisitor(
    val replacedValue: Int,
    api: Int,
    nextVisitor: MethodVisitor
) : MethodVisitor(api, nextVisitor) {

    override fun visitInsn(opcode: Int) {
        if (opcode == Opcodes.IRETURN) {
            visitLdcInsn(replacedValue)
        }
        super.visitInsn(opcode)
    }
}

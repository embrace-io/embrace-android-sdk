package io.embrace.android.gradle.plugin.instrumentation.config

import org.objectweb.asm.MethodVisitor
import org.objectweb.asm.Opcodes

/**
 * Visits a boolean method and replaces its return value with the [replacedValue] parameter.
 */
class BooleanReturnValueMethodVisitor(
    val replacedValue: Boolean,
    api: Int,
    nextVisitor: MethodVisitor
) : MethodVisitor(api, nextVisitor) {

    override fun visitInsn(opcode: Int) {
        if (opcode == Opcodes.IRETURN) {
            if (replacedValue) {
                visitInsn(Opcodes.ICONST_1)
            } else {
                visitInsn(Opcodes.ICONST_0)
            }
        }
        super.visitInsn(opcode)
    }
}

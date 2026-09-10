package io.embrace.android.gradle.plugin.instrumentation.config

import org.objectweb.asm.MethodVisitor
import org.objectweb.asm.Opcodes

/**
 * Visits a method that returns an enum and replaces its return value with the [replacedValue]
 * constant of the enum named by [enumInternalName].
 *
 * Enum constants are static fields on the enum class, so unlike the other return types this reads
 * the value rather than loading a constant.
 */
class EnumReturnValueMethodVisitor(
    val enumInternalName: String,
    val replacedValue: String,
    api: Int,
    nextVisitor: MethodVisitor,
) : MethodVisitor(api, nextVisitor) {

    override fun visitInsn(opcode: Int) {
        if (opcode == Opcodes.ARETURN) {
            visitFieldInsn(Opcodes.GETSTATIC, enumInternalName, replacedValue, "L$enumInternalName;")
        }
        super.visitInsn(opcode)
    }
}

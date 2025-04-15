package io.embrace.android.gradle.plugin.instrumentation.visitor

import org.objectweb.asm.MethodVisitor
import org.objectweb.asm.Opcodes
import org.objectweb.asm.Type

/**
 * Visits a method that should be rewritten to include an API call to instrumentation
 * and inserts a call to a static method at the very start.
 */
internal class InstrumentationTargetMethodVisitor(
    api: Int,
    methodVisitor: MethodVisitor?,
    private val params: BytecodeMethodInsertionParams,
) : MethodVisitor(api, methodVisitor) {

    override fun visitCode() {
        // count how many parameters are in the method descriptor
        val paramCount = Type.getArgumentTypes(params.descriptor).size

        // load local variables and push onto the operand stack
        repeat(paramCount) {
            visitVarInsn(Opcodes.ALOAD, it + params.startVarIndex)
        }

        // invoke the target method
        visitMethodInsn(
            Opcodes.INVOKESTATIC,
            params.owner,
            params.name,
            params.descriptor,
            false
        )

        // call super last to reduce chance of interference with other bytecode instrumentation
        super.visitCode()
    }
}

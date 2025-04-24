package io.embrace.android.gradle.plugin.instrumentation.visitor

import org.objectweb.asm.MethodVisitor
import org.objectweb.asm.Opcodes

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
        // load local variables required for method call (if any) and push onto the operand stack
        params.operandStackIndices.forEach {
            visitVarInsn(Opcodes.ALOAD, it)
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

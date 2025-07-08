package io.embrace.android.gradle.plugin.instrumentation.visitor

import org.objectweb.asm.MethodVisitor
import org.objectweb.asm.Opcodes

/**
 * Visits a method that should be rewritten to include an API call to instrumentation
 * and inserts a call to a static method before all return statements (at the end).
 */
internal class InstrumentationTargetMethodEndVisitor(
    api: Int,
    methodVisitor: MethodVisitor?,
    private val params: BytecodeMethodInsertionParams,
) : MethodVisitor(api, methodVisitor) {

    override fun visitInsn(opcode: Int) {
        if (isReturnInstruction(opcode)) {
            // Insert call before return instruction
            insertCall()
        }
        super.visitInsn(opcode)
    }

    private fun insertCall() {
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
    }

    // Returns true if the instruction is a return or throw instruction.
    private fun isReturnInstruction(opcode: Int): Boolean {
        return opcode == Opcodes.RETURN ||
            opcode == Opcodes.IRETURN ||
            opcode == Opcodes.LRETURN ||
            opcode == Opcodes.FRETURN ||
            opcode == Opcodes.DRETURN ||
            opcode == Opcodes.ARETURN ||
            opcode == Opcodes.ATHROW
    }
}

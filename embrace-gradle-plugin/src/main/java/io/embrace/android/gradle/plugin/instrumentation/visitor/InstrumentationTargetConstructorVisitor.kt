package io.embrace.android.gradle.plugin.instrumentation.visitor

import org.objectweb.asm.MethodVisitor
import org.objectweb.asm.Opcodes

/**
 * Visits a constructor and inserts a call to a static method before each of its return statements, leaving
 * constructors that delegate via this(...) unchanged so the call happens once per constructed object.
 */
internal class InstrumentationTargetConstructorVisitor(
    api: Int,
    methodVisitor: MethodVisitor?,
    private val className: String,
    private val params: BytecodeMethodInsertionParams,
) : MethodVisitor(api, methodVisitor) {

    // number of objects created with NEW that have not yet had their constructor invoked
    private var uninitializedCount = 0
    private var initialized = false
    private var delegated = false

    override fun visitTypeInsn(opcode: Int, type: String) {
        if (!initialized && opcode == Opcodes.NEW) {
            uninitializedCount++
        }
        super.visitTypeInsn(opcode, type)
    }

    override fun visitMethodInsn(
        opcode: Int,
        owner: String,
        name: String,
        descriptor: String,
        isInterface: Boolean,
    ) {
        super.visitMethodInsn(opcode, owner, name, descriptor, isInterface)

        if (initialized || opcode != Opcodes.INVOKESPECIAL || name != "<init>") {
            return
        }
        if (uninitializedCount > 0) {
            // initializes an object created with NEW, e.g. a constructor argument
            uninitializedCount--
            return
        }

        // this is the super(...) or this(...) call that initializes 'this'
        initialized = true
        delegated = owner == className
    }

    override fun visitInsn(opcode: Int) {
        // ATHROW is skipped: the object failed to construct, and 'this' may not be initialized yet
        if (opcode == Opcodes.RETURN && initialized && !delegated) {
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
        visitMethodInsn(Opcodes.INVOKESTATIC, params.owner, params.name, params.descriptor, false)
    }
}

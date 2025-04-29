package io.embrace.android.gradle.plugin.instrumentation.visitor

import org.objectweb.asm.ClassVisitor
import org.objectweb.asm.MethodVisitor
import org.objectweb.asm.Opcodes
import org.objectweb.asm.Type

/**
 * Visits the class and adds an override that calls to the super class method, if no override is already specified.
 */
class InsertSuperOverrideClassVisitor(
    api: Int,
    nextClassVisitor: ClassVisitor?,
    private val feature: BytecodeInstrumentationFeature,
) : ClassVisitor(api, nextClassVisitor) {

    private var hasOverride = false

    override fun visitMethod(
        access: Int,
        name: String,
        desc: String,
        signature: String?,
        exceptions: Array<String>?,
    ): MethodVisitor? {
        val nextMethodVisitor = super.visitMethod(access, name, desc, signature, exceptions)

        return if (feature.targetParams.name == name && feature.targetParams.descriptor == desc && !isStatic(access)) {
            hasOverride = true
            InstrumentationTargetMethodVisitor(
                api = api,
                methodVisitor = nextMethodVisitor,
                params = feature.insertionParams
            )
        } else {
            return nextMethodVisitor
        }
    }

    override fun visitEnd() {
        // add an override of onPageStarted if the class does not have one already.
        if (!hasOverride) {
            val nextMethodVisitor = super.visitMethod(
                Opcodes.ACC_PUBLIC,
                feature.targetParams.name,
                feature.targetParams.descriptor,
                null,
                emptyArray()
            )
            nextMethodVisitor.addSuperCall()
        }
        super.visitEnd()
    }

    private fun MethodVisitor.addSuperCall() {
        val count = Type.getType(feature.addOverrideParams?.descriptor).argumentCount + 1
        repeat(count) {
            visitVarInsn(Opcodes.ALOAD, it)
        }

        visitMethodInsn(
            Opcodes.INVOKESPECIAL,
            feature.addOverrideParams?.owner,
            feature.addOverrideParams?.name,
            feature.addOverrideParams?.descriptor,
            false
        )

        // load local variables required for method call (if any) and push onto the operand stack
        val params = feature.insertionParams
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

        visitEnd()
        visitInsn(Opcodes.RETURN)
        visitMaxs(count, 0)
    }
}

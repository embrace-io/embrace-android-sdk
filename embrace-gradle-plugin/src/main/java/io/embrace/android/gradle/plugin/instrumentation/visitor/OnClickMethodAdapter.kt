package io.embrace.android.gradle.plugin.instrumentation.visitor

import org.objectweb.asm.MethodVisitor
import org.objectweb.asm.Opcodes

/**
 * Visits an onClick method and inserts a call to ViewSwazzledHooks._preOnClick at the very start
 * of the method.
 */
class OnClickMethodAdapter(
    api: Int,
    methodVisitor: MethodVisitor?
) : MethodVisitor(api, methodVisitor) {

    override fun visitCode() {
        // load local variable 'this' and push it onto the operand stack
        visitVarInsn(Opcodes.ALOAD, 0)

        // load local variable 'view' and push it onto the operand stack
        visitVarInsn(Opcodes.ALOAD, 1)

        // invoke ViewSwazzledHooks$OnClickListener._preOnClick()
        visitMethodInsn(
            Opcodes.INVOKESTATIC,
            "io/embrace/android/embracesdk/ViewSwazzledHooks\$OnClickListener",
            "_preOnClick",
            "(Landroid/view/View\$OnClickListener;Landroid/view/View;)V",
            false
        )

        // call super last to reduce chance of interference with other bytecode instrumentation
        super.visitCode()
    }
}

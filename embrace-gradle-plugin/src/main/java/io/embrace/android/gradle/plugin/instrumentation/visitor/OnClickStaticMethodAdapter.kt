package io.embrace.android.gradle.plugin.instrumentation.visitor

import org.objectweb.asm.MethodVisitor
import org.objectweb.asm.Opcodes

/**
 * Visits a static onClick method and inserts a call to ViewSwazzledHooks._preOnClick at the very
 * start of the method.
 */
class OnClickStaticMethodAdapter(
    api: Int,
    methodVisitor: MethodVisitor?,
) : MethodVisitor(api, methodVisitor) {

    override fun visitCode() {
        // load the null reference and push it onto the operand stack.
        // null is ok here as _preOnClick doesn't use the listener,
        // and in a static context 'this' does not actually implement OnClickListener
        // in Java bytecode.
        visitInsn(Opcodes.ACONST_NULL)

        // load local variable 'view' and push it onto the operand stack
        visitVarInsn(Opcodes.ALOAD, 0)

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

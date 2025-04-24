package io.embrace.android.gradle.plugin.instrumentation.visitor

import org.objectweb.asm.MethodVisitor
import org.objectweb.asm.Opcodes

/**
 * Visits a static onLongClick method and inserts a call to OnLongClickBytecodeEntrypoint.onLongClick
 * at the very start of the method.
 */
class OnLongClickStaticMethodAdapter(
    api: Int,
    methodVisitor: MethodVisitor?,
) : MethodVisitor(api, methodVisitor) {

    override fun visitCode() {
        // load the null reference and push it onto the operand stack.
        // null is ok here as _preOnLongClick doesn't use the listener,
        // and in a static context 'this' does not actually implement OnLongClickListener
        // in Java bytecode.
        visitInsn(Opcodes.ACONST_NULL)

        // load local variable 'view' and push it onto the operand stack
        visitVarInsn(Opcodes.ALOAD, 0)

        // invoke OnLongClickBytecodeEntrypoint.onLongClick()
        visitMethodInsn(
            Opcodes.INVOKESTATIC,
            "io/embrace/android/embracesdk/internal/instrumentation/bytecode/OnLongClickBytecodeEntrypoint",
            "onLongClick",
            "(Landroid/view/View\$OnLongClickListener;Landroid/view/View;)V",
            false
        )

        // call super last to reduce chance of interference with other bytecode instrumentation
        super.visitCode()
    }
}

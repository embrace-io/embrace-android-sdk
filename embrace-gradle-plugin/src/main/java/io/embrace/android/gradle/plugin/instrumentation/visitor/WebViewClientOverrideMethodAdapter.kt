package io.embrace.android.gradle.plugin.instrumentation.visitor

import org.objectweb.asm.MethodVisitor
import org.objectweb.asm.Opcodes

/**
 * Creates an onPageStarted method override and inserts a call to
 * ViewSwazzledHooks._preOnPageStarted at the very start of the method.
 */
class WebViewClientOverrideMethodAdapter(
    api: Int,
    methodVisitor: MethodVisitor?
) : WebViewClientMethodAdapter(api, methodVisitor) {

    override fun visitEnd() {
        instrumentOnPageStarted()
        addSuperCall()
        super.visitEnd()
        visitInsn(Opcodes.RETURN)
        visitMaxs(4, 0)
    }

    private fun addSuperCall() {
        // load local variable 'this' and push it onto the operand stack
        visitVarInsn(Opcodes.ALOAD, 0)

        // load local variable 'view' and push it onto the operand stack
        visitVarInsn(Opcodes.ALOAD, 1)

        // load local variable 'url' and push it onto the operand stack
        visitVarInsn(Opcodes.ALOAD, 2)

        // load local variable 'favicon' and push it onto the operand stack
        visitVarInsn(Opcodes.ALOAD, 3)

        visitMethodInsn(
            Opcodes.INVOKESPECIAL,
            "android/webkit/WebViewClient",
            "onPageStarted",
            "(Landroid/webkit/WebView;Ljava/lang/String;Landroid/graphics/Bitmap;)V",
            false
        )
    }
}

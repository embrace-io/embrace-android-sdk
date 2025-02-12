package io.embrace.android.gradle.plugin.instrumentation.visitor

import org.objectweb.asm.MethodVisitor
import org.objectweb.asm.Opcodes

/**
 * Visits the onPageStarted method and inserts a call to ViewSwazzledHooks._preOnPageStarted
 * at the very start of the method.
 */
open class WebViewClientMethodAdapter(
    api: Int,
    methodVisitor: MethodVisitor?
) : MethodVisitor(api, methodVisitor) {

    override fun visitCode() {
        instrumentOnPageStarted()
        // call super last to reduce chance of interference with other bytecode instrumentation
        super.visitCode()
    }

    internal fun instrumentOnPageStarted() {
        // load local variable 'view' and push it onto the operand stack
        visitVarInsn(Opcodes.ALOAD, 1)

        // load local variable 'url' and push it onto the operand stack
        visitVarInsn(Opcodes.ALOAD, 2)

        // load local variable 'favicon' and push it onto the operand stack
        visitVarInsn(Opcodes.ALOAD, 3)

        // invoke WebViewClientSwazzledHooks._preOnPageStarted()
        visitMethodInsn(
            Opcodes.INVOKESTATIC,
            "io/embrace/android/embracesdk/WebViewClientSwazzledHooks",
            "_preOnPageStarted",
            "(Landroid/webkit/WebView;Ljava/lang/String;Landroid/graphics/Bitmap;)V",
            false
        )
    }
}

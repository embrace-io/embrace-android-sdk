package io.embrace.android.gradle.plugin.instrumentation.visitor

import org.objectweb.asm.ClassVisitor
import org.objectweb.asm.MethodVisitor
import org.objectweb.asm.Opcodes

/**
 * Visits the [WebViewClient] class and returns a [WebViewClientMethodAdapter] for the
 * onPageStarted method.
 */
class WebViewClientOverrideClassAdapter(
    api: Int,
    internal val nextClassVisitor: ClassVisitor?,
) : ClassVisitor(api, nextClassVisitor) {

    companion object {
        private const val METHOD_NAME = "onPageStarted"
        private const val METHOD_DESC =
            "(Landroid/webkit/WebView;Ljava/lang/String;Landroid/graphics/Bitmap;)V"
    }

    private var hasOverride = false

    override fun visitMethod(
        access: Int,
        name: String,
        desc: String,
        signature: String?,
        exceptions: Array<String>?,
    ): MethodVisitor? {
        if (METHOD_NAME == name && METHOD_DESC == desc) {
            hasOverride = true
        }
        return super.visitMethod(access, name, desc, signature, exceptions)
    }

    override fun visitEnd() {
        // add an override of onPageStarted if the class does not have one already.
        if (!hasOverride) {
            val nextMethodVisitor = super.visitMethod(
                Opcodes.ACC_PUBLIC,
                METHOD_NAME,
                METHOD_DESC,
                null,
                emptyArray()
            )
            nextMethodVisitor.addSuperCall()
        }
        super.visitEnd()
    }

    private fun MethodVisitor.addSuperCall() {
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
        visitEnd()
        visitInsn(Opcodes.RETURN)
        visitMaxs(4, 0)
    }
}

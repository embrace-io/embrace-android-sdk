package io.embrace.android.gradle.plugin.instrumentation.visitor

import org.objectweb.asm.ClassVisitor
import org.objectweb.asm.MethodVisitor
import org.objectweb.asm.Opcodes

/**
 * Visits the [WebViewClient] class and returns a [WebViewClientMethodAdapter] for the
 * onPageStarted method.
 */
class WebViewClientClassAdapter(
    api: Int,
    internal val nextClassVisitor: ClassVisitor?,
) : ClassVisitor(api, nextClassVisitor) {

    companion object {
        const val CLASS_NAME = "android.webkit.WebViewClient"
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
        val nextMethodVisitor = super.visitMethod(access, name, desc, signature, exceptions)

        return if (METHOD_NAME == name && METHOD_DESC == desc) {
            hasOverride = true
            InstrumentationTargetMethodVisitor(
                api = api,
                methodVisitor = nextMethodVisitor,
                params = BytecodeMethodInsertionParams(
                    owner = "io/embrace/android/embracesdk/WebViewClientSwazzledHooks",
                    name = "_preOnPageStarted",
                    descriptor = "(Landroid/webkit/WebView;Ljava/lang/String;Landroid/graphics/Bitmap;)V",
                    startVarIndex = 1,
                )
            )
        } else {
            nextMethodVisitor
        }
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
            WebViewClientOverrideMethodAdapter(api, nextMethodVisitor).visitEnd()
        }
        super.visitEnd()
    }
}

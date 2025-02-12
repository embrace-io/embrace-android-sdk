package io.embrace.android.gradle.plugin.instrumentation.visitor

import com.android.build.api.instrumentation.ClassContext
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
    private val logger: (() -> String) -> Unit
) : ClassVisitor(api, nextClassVisitor) {

    companion object : ClassVisitFilter {
        private const val CLASS_NAME = "android.webkit.WebViewClient"
        private const val METHOD_NAME = "onPageStarted"
        private const val METHOD_DESC =
            "(Landroid/webkit/WebView;Ljava/lang/String;Landroid/graphics/Bitmap;)V"

        override fun accept(classContext: ClassContext): Boolean {
            if (classContext.currentClassData.superClasses.contains(CLASS_NAME)) {
                return true
            }
            return false
        }
    }

    var hasOverride = false

    override fun visitMethod(
        access: Int,
        name: String,
        desc: String,
        signature: String?,
        exceptions: Array<String>?
    ): MethodVisitor? {
        val nextMethodVisitor = super.visitMethod(access, name, desc, signature, exceptions)

        return if (METHOD_NAME == name && METHOD_DESC == desc) {
            logger { "WebViewClientClassAdapter: instrumented method $name $desc" }
            hasOverride = true
            WebViewClientMethodAdapter(api, nextMethodVisitor)
        } else {
            nextMethodVisitor
        }
    }

    override fun visitEnd() {
        // add an override of onPageStarted if the class does not have one already.
        if (!hasOverride) {
            logger { "WebViewClientClassAdapter: instrumented method $METHOD_NAME $METHOD_DESC (added override)" }

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

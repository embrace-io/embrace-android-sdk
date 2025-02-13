package io.embrace.android.gradle.plugin.instrumentation.visitor

import com.android.build.api.instrumentation.ClassContext
import org.objectweb.asm.ClassVisitor
import org.objectweb.asm.MethodVisitor

/**
 * Visits a class and returns an [OnClickMethodAdapter] for any onClick method which
 * conforms to the OnClickListener interface.
 */
class OnClickClassAdapter(
    api: Int,
    internal val nextClassVisitor: ClassVisitor?,
    private val logger: (() -> String) -> Unit
) : ClassVisitor(api, nextClassVisitor) {

    companion object : ClassVisitFilter {
        private const val METHOD_NAME = "onClick"
        private const val METHOD_DESC = "(Landroid/view/View;)V"

        override fun accept(classContext: ClassContext) = true
    }

    override fun visitMethod(
        access: Int,
        name: String,
        desc: String,
        signature: String?,
        exceptions: Array<String>?
    ): MethodVisitor? {
        val nextMethodVisitor = super.visitMethod(access, name, desc, signature, exceptions)

        return if (METHOD_NAME == name && METHOD_DESC == desc && !isStatic(access)) {
            logger { "OnClickClassAdapter: instrumented method $name $desc" }
            OnClickMethodAdapter(api, nextMethodVisitor)
        } else if (METHOD_DESC == desc && isStatic(access) && isSynthetic(access)) {
            logger { "OnClickClassAdapter: instrumented synthetic method $name $desc" }
            OnClickStaticMethodAdapter(api, nextMethodVisitor)
        } else {
            nextMethodVisitor
        }
    }
}

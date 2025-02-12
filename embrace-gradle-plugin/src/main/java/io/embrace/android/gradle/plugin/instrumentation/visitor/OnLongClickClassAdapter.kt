package io.embrace.android.gradle.plugin.instrumentation.visitor

import com.android.build.api.instrumentation.ClassContext
import org.objectweb.asm.ClassVisitor
import org.objectweb.asm.MethodVisitor

/**
 * Visits a class and returns an [OnLongClickMethodAdapter] for any onLongClick method which
 * conforms to the OnLongClickListener interface.
 */
class OnLongClickClassAdapter(
    api: Int,
    internal val nextClassVisitor: ClassVisitor?,
    private val logger: (() -> String) -> Unit
) : ClassVisitor(api, nextClassVisitor) {

    companion object : ClassVisitFilter {
        private const val METHOD_NAME = "onLongClick"
        private const val METHOD_DESC = "(Landroid/view/View;)Z"

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
            logger { "OnLongClickClassAdapter: instrumented method $name $desc" }
            OnLongClickMethodAdapter(api, nextMethodVisitor)
        } else if (METHOD_DESC == desc && isStatic(access) && isSynthetic(access)) {
            logger { "OnLongClickClassAdapter: instrumented synthetic method $name $desc" }
            OnLongClickStaticMethodAdapter(api, nextMethodVisitor)
        } else {
            nextMethodVisitor
        }
    }
}

package io.embrace.android.gradle.plugin.instrumentation.visitor

import org.objectweb.asm.ClassVisitor
import org.objectweb.asm.MethodVisitor

/**
 * Visits a class and returns an [OnLongClickMethodAdapter] for any onLongClick method which
 * conforms to the OnLongClickListener interface.
 */
class OnLongClickClassAdapter(
    api: Int,
    internal val nextClassVisitor: ClassVisitor?,
) : ClassVisitor(api, nextClassVisitor) {

    companion object {
        private const val METHOD_NAME = "onLongClick"
        private const val METHOD_DESC = "(Landroid/view/View;)Z"
    }

    override fun visitMethod(
        access: Int,
        name: String,
        desc: String,
        signature: String?,
        exceptions: Array<String>?,
    ): MethodVisitor? {
        val nextMethodVisitor = super.visitMethod(access, name, desc, signature, exceptions)
        val regularMatch = METHOD_NAME == name && METHOD_DESC == desc && !isStatic(access)
        val syntheticMatch = METHOD_DESC == desc && isStatic(access) && isSynthetic(access)

        return if (regularMatch || syntheticMatch) {
            InstrumentationTargetMethodVisitor(
                api = api,
                methodVisitor = nextMethodVisitor,
                params = BytecodeMethodInsertionParams(
                    owner = "io/embrace/android/embracesdk/ViewSwazzledHooks\$OnLongClickListener",
                    name = "_preOnLongClick",
                    descriptor = "(Landroid/view/View;)V",
                )
            )
        } else {
            nextMethodVisitor
        }
    }
}

package io.embrace.android.gradle.plugin.instrumentation.visitor

import org.objectweb.asm.ClassVisitor
import org.objectweb.asm.MethodVisitor

/**
 * Visits a class and returns an [OnClickMethodAdapter] for any onClick method which
 * conforms to the OnClickListener interface.
 */
class OnClickClassAdapter(
    api: Int,
    internal val nextClassVisitor: ClassVisitor?,
) : ClassVisitor(api, nextClassVisitor) {

    companion object {
        private const val METHOD_NAME = "onClick"
        private const val METHOD_DESC = "(Landroid/view/View;)V"
    }

    override fun visitMethod(
        access: Int,
        name: String,
        desc: String,
        signature: String?,
        exceptions: Array<String>?,
    ): MethodVisitor? {
        val nextMethodVisitor = super.visitMethod(access, name, desc, signature, exceptions)
        val static = isStatic(access)
        val isVirtualMethod = METHOD_NAME == name && METHOD_DESC == desc && !static
        val isStaticMethod = METHOD_DESC == desc && static && isSynthetic(access)

        return if (isVirtualMethod || isStaticMethod) {
            val indices = when (static) {
                true -> listOf(0)
                else -> listOf(1)
            }
            InstrumentationTargetMethodVisitor(
                api = api,
                methodVisitor = nextMethodVisitor,
                params = BytecodeMethodInsertionParams(
                    owner = "io/embrace/android/embracesdk/internal/instrumentation/bytecode/OnClickBytecodeEntrypoint",
                    name = "onClick",
                    descriptor = "(Landroid/view/View;)V",
                    operandStackIndices = indices,
                )
            )
        } else {
            nextMethodVisitor
        }
    }
}

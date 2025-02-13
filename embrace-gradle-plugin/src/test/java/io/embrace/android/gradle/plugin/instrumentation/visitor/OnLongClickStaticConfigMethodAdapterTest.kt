package io.embrace.android.gradle.plugin.instrumentation.visitor

import io.mockk.mockk
import io.mockk.verify
import org.junit.Test
import org.objectweb.asm.MethodVisitor
import org.objectweb.asm.Opcodes

class OnLongClickStaticConfigMethodAdapterTest {

    @Test
    fun visitCode() {
        val visitor = mockk<MethodVisitor>(relaxed = true)
        OnLongClickStaticMethodAdapter(Opcodes.ASM6, visitor).visitCode()
        verify(exactly = 1) {
            with(visitor) {
                visitInsn(Opcodes.ACONST_NULL)
                visitVarInsn(Opcodes.ALOAD, 0)
                visitMethodInsn(
                    Opcodes.INVOKESTATIC,
                    "io/embrace/android/embracesdk/ViewSwazzledHooks\$OnLongClickListener",
                    "_preOnLongClick",
                    "(Landroid/view/View\$OnLongClickListener;Landroid/view/View;)V",
                    false
                )
                visitCode()
            }
        }
    }
}

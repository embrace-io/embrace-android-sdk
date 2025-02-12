package io.embrace.android.gradle.plugin.instrumentation.visitor

import io.mockk.mockk
import io.mockk.verify
import org.junit.Test
import org.objectweb.asm.MethodVisitor
import org.objectweb.asm.Opcodes

class OnClickConfigMethodAdapterTest {

    @Test
    fun visitCode() {
        val visitor = mockk<MethodVisitor>(relaxed = true)
        OnClickMethodAdapter(Opcodes.ASM6, visitor).visitCode()
        verify(exactly = 1) {
            with(visitor) {
                visitVarInsn(Opcodes.ALOAD, 0)
                visitVarInsn(Opcodes.ALOAD, 1)
                visitMethodInsn(
                    Opcodes.INVOKESTATIC,
                    "io/embrace/android/embracesdk/ViewSwazzledHooks\$OnClickListener",
                    "_preOnClick",
                    "(Landroid/view/View\$OnClickListener;Landroid/view/View;)V",
                    false
                )
                visitCode()
            }
        }
    }
}

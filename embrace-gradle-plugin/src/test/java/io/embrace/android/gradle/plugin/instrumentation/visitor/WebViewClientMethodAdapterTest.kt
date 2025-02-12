package io.embrace.android.gradle.plugin.instrumentation.visitor

import io.mockk.mockk
import io.mockk.verify
import org.junit.Test
import org.objectweb.asm.MethodVisitor
import org.objectweb.asm.Opcodes

class WebViewClientMethodAdapterTest {

    @Test
    fun visitCode() {
        val visitor = mockk<MethodVisitor>(relaxed = true)
        WebViewClientMethodAdapter(Opcodes.ASM6, visitor).visitCode()
        verify(exactly = 1) {
            with(visitor) {
                visitVarInsn(Opcodes.ALOAD, 1)
                visitVarInsn(Opcodes.ALOAD, 2)
                visitVarInsn(Opcodes.ALOAD, 3)
                visitMethodInsn(
                    Opcodes.INVOKESTATIC,
                    "io/embrace/android/embracesdk/WebViewClientSwazzledHooks",
                    "_preOnPageStarted",
                    "(Landroid/webkit/WebView;Ljava/lang/String;Landroid/graphics/Bitmap;)V",
                    false
                )
            }
        }
    }
}

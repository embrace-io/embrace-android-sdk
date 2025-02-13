package io.embrace.android.gradle.plugin.instrumentation.visitor

import io.mockk.mockk
import io.mockk.verify
import org.junit.Test
import org.objectweb.asm.MethodVisitor
import org.objectweb.asm.Opcodes

class OkHttpConfigMethodAdapterTest {

    @Test
    fun visitCode() {
        val visitor = mockk<MethodVisitor>(relaxed = true)
        OkHttpMethodAdapter(Opcodes.ASM6, visitor).visitCode()
        verify(exactly = 1) {
            with(visitor) {
                visitVarInsn(Opcodes.ALOAD, 0)
                visitMethodInsn(
                    Opcodes.INVOKESTATIC,
                    "io/embrace/android/embracesdk/okhttp3/swazzle/callback/okhttp3/OkHttpClient\$Builder",
                    "_preBuild",
                    "(Lokhttp3/OkHttpClient\$Builder;)V",
                    false
                )
                visitCode()
            }
        }
    }
}

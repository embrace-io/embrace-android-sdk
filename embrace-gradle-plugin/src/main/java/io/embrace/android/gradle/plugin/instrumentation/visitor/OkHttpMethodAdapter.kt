package io.embrace.android.gradle.plugin.instrumentation.visitor

import org.objectweb.asm.MethodVisitor
import org.objectweb.asm.Opcodes

/**
 * Visits OkHttp methods and inserts a call to OkHttpClient$Builder._preBuild at the very start
 * of the method.
 */
class OkHttpMethodAdapter(
    api: Int,
    methodVisitor: MethodVisitor?
) : MethodVisitor(api, methodVisitor) {

    override fun visitCode() {
        // load local variable 'this' and push it onto the operand stack
        visitVarInsn(Opcodes.ALOAD, 0)

        // invoke Builder._preBuild(this)
        visitMethodInsn(
            Opcodes.INVOKESTATIC,
            "io/embrace/android/embracesdk/okhttp3/swazzle/callback/okhttp3/OkHttpClient\$Builder",
            "_preBuild",
            "(Lokhttp3/OkHttpClient\$Builder;)V",
            false
        )

        // call super last to reduce chance of interference with other bytecode instrumentation
        super.visitCode()
    }
}

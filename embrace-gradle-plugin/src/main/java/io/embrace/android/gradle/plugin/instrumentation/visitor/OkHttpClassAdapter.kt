package io.embrace.android.gradle.plugin.instrumentation.visitor

import org.objectweb.asm.ClassVisitor
import org.objectweb.asm.MethodVisitor

/**
 * Visits a class and returns an [OkHttpInitMethodAdapter] for OkHttp$Builder methods.
 */
class OkHttpClassAdapter(
    api: Int,
    internal val nextClassVisitor: ClassVisitor?,
) : ClassVisitor(api, nextClassVisitor) {

    companion object {
        const val CLASS_NAME = "okhttp3.OkHttpClient\$Builder"
        private const val METHOD_NAME_BUILD = "build"
        private const val METHOD_DESC_BUILD = "()Lokhttp3/OkHttpClient;"
    }

    override fun visitMethod(
        access: Int,
        name: String,
        desc: String,
        signature: String?,
        exceptions: Array<String>?,
    ): MethodVisitor? {
        val nextMethodVisitor = super.visitMethod(access, name, desc, signature, exceptions)

        return if (METHOD_NAME_BUILD == name && METHOD_DESC_BUILD == desc) {
            InstrumentationTargetMethodVisitor(
                api = api,
                methodVisitor = nextMethodVisitor,
                params = BytecodeMethodInsertionParams(
                    owner = "io/embrace/android/embracesdk/okhttp3/swazzle/callback/okhttp3/OkHttpClient\$Builder",
                    name = "_preBuild",
                    descriptor = "(Lokhttp3/OkHttpClient\$Builder;)V",
                )
            )
        } else {
            nextMethodVisitor
        }
    }
}

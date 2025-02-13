package io.embrace.android.gradle.plugin.instrumentation.visitor

import com.android.build.api.instrumentation.ClassContext
import org.objectweb.asm.ClassVisitor
import org.objectweb.asm.MethodVisitor

/**
 * Visits a class and returns an [OkHttpInitMethodAdapter] for OkHttp$Builder methods.
 */
class OkHttpClassAdapter(
    api: Int,
    internal val nextClassVisitor: ClassVisitor?,
    private val logger: (() -> String) -> Unit
) : ClassVisitor(api, nextClassVisitor) {

    companion object : ClassVisitFilter {
        private const val CLASS_NAME = "okhttp3.OkHttpClient\$Builder"
        private const val METHOD_NAME_BUILD = "build"
        private const val METHOD_DESC_BUILD = "()Lokhttp3/OkHttpClient;"

        override fun accept(classContext: ClassContext): Boolean {
            return classContext.currentClassData.className == CLASS_NAME
        }
    }

    override fun visitMethod(
        access: Int,
        name: String,
        desc: String,
        signature: String?,
        exceptions: Array<String>?
    ): MethodVisitor? {
        val nextMethodVisitor = super.visitMethod(access, name, desc, signature, exceptions)

        return if (METHOD_NAME_BUILD == name && METHOD_DESC_BUILD == desc) {
            logger { "OkHttpClassAdapter: instrumented method $name $desc" }
            OkHttpMethodAdapter(api, nextMethodVisitor)
        } else {
            nextMethodVisitor
        }
    }
}

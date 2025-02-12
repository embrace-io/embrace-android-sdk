package io.embrace.android.gradle.plugin.instrumentation.config

import org.objectweb.asm.MethodVisitor
import org.objectweb.asm.Opcodes

/**
 * Visits a Map method and replaces its return value with the [replacedValue] parameter.
 */
class MapReturnValueMethodVisitor(
    val replacedValue: Map<String, String>,
    api: Int,
    nextVisitor: MethodVisitor
) : MethodVisitor(api, nextVisitor) {

    override fun visitCode() {
        super.visitCode()

        // instantiate a new map
        visitTypeInsn(Opcodes.NEW, "java/util/HashMap")
        visitInsn(Opcodes.DUP)
        visitMethodInsn(Opcodes.INVOKESPECIAL, "java/util/HashMap", "<init>", "()V", false)

        // iterate replacedValue and add each object to the list
        replacedValue.forEach { entry ->
            visitInsn(Opcodes.DUP)
            visitLdcInsn(entry.key)
            visitLdcInsn(entry.value)
            visitMethodInsn(
                Opcodes.INVOKEVIRTUAL,
                "java/util/HashMap",
                "put",
                "(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;",
                false
            )
            visitInsn(Opcodes.POP) // pop return value off stack
        }
        visitInsn(Opcodes.ARETURN)
    }
}

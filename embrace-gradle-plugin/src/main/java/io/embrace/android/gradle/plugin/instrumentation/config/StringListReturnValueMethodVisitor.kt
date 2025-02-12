package io.embrace.android.gradle.plugin.instrumentation.config

import org.objectweb.asm.MethodVisitor
import org.objectweb.asm.Opcodes

/**
 * Visits a string method and replaces its return value with the [replacedValue] parameter.
 */
class StringListReturnValueMethodVisitor(
    val replacedValue: List<String>,
    api: Int,
    nextVisitor: MethodVisitor
) : MethodVisitor(api, nextVisitor) {

    override fun visitCode() {
        super.visitCode()

        // instantiate a new array list
        visitTypeInsn(Opcodes.NEW, "java/util/ArrayList")
        visitInsn(Opcodes.DUP)
        visitMethodInsn(Opcodes.INVOKESPECIAL, "java/util/ArrayList", "<init>", "()V", false)

        // iterate replacedValue and add each object to the list
        replacedValue.forEach { value ->
            visitInsn(Opcodes.DUP)
            visitLdcInsn(value)
            visitMethodInsn(
                Opcodes.INVOKEVIRTUAL,
                "java/util/ArrayList",
                "add",
                "(Ljava/lang/Object;)Z",
                false
            )
            visitInsn(Opcodes.POP) // pop return value off stack
        }
        visitInsn(Opcodes.ARETURN)
    }
}

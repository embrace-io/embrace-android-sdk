package io.embrace.android.gradle.plugin.instrumentation.visitor

import org.objectweb.asm.MethodVisitor
import org.objectweb.asm.Opcodes

/**
 * Visits the onCreate method and inserts a call to AutoStartInstrumentationHook._preOnCreate at the very start
 * of the method. This ensures that Embrace is started before any other initialization code runs.
 */
internal class ApplicationMethodAdapter(
    api: Int,
    methodVisitor: MethodVisitor?
) : MethodVisitor(api, methodVisitor) {

    override fun visitCode() {
        // invoke AutoStartInstrumentationHook$Application._preOnCreate()
        visitVarInsn(Opcodes.ALOAD, 0) // load 'this' onto the stack
        visitMethodInsn(
            Opcodes.INVOKESTATIC,
            "io/embrace/android/embracesdk/AutoStartInstrumentationHook",
            "_preOnCreate",
            "(Landroid/app/Application;)V",
            false
        )

        // call super last to reduce chance of interference with other bytecode instrumentation
        super.visitCode()
    }
}

package io.embrace.android.gradle.plugin.instrumentation.visitor

import org.objectweb.asm.MethodVisitor
import org.objectweb.asm.Opcodes

/**
 * Visits the onMessageReceived method and inserts a call to
 * FirebaseSwazzledHooks._onMessageReceived at the very start of the method.
 */
class FirebaseMessagingServiceMethodAdapter(
    api: Int,
    methodVisitor: MethodVisitor?
) : MethodVisitor(api, methodVisitor) {

    override fun visitCode() {
        // load local variable 'remoteMessage' and push it onto the operand stack
        visitVarInsn(Opcodes.ALOAD, 1)
        // invoke FirebaseSwazzledHooks._onMessageReceived()
        visitMethodInsn(
            Opcodes.INVOKESTATIC,
            "io/embrace/android/embracesdk/fcm/swazzle/callback/com/android/fcm/FirebaseSwazzledHooks",
            "_onMessageReceived",
            "(Lcom/google/firebase/messaging/RemoteMessage;)V",
            false
        )
        // call super last to reduce chance of interference with other bytecode instrumentation
        super.visitCode()
    }
}

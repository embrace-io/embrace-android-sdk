package io.embrace.android.gradle.plugin.instrumentation.visitor

import com.android.build.api.instrumentation.ClassContext
import org.objectweb.asm.ClassVisitor
import org.objectweb.asm.MethodVisitor

/**
 * Visits the [FirebaseMessagingService] class and returns a [FirebaseMessagingServiceMethodAdapter]
 * for the onMessageReceived method.
 */
class FirebaseMessagingServiceClassAdapter(
    api: Int,
    internal val nextClassVisitor: ClassVisitor?,
) : ClassVisitor(api, nextClassVisitor) {

    companion object : ClassVisitFilter {
        private const val CLASS_NAME = "com.google.firebase.messaging.FirebaseMessagingService"
        private const val METHOD_NAME = "onMessageReceived"
        private const val METHOD_DESC = "(Lcom/google/firebase/messaging/RemoteMessage;)V"

        override fun accept(classContext: ClassContext): Boolean {
            return classContext.currentClassData.superClasses.contains(CLASS_NAME)
        }
    }

    override fun visitMethod(
        access: Int,
        name: String,
        desc: String,
        signature: String?,
        exceptions: Array<String>?,
    ): MethodVisitor? {
        val nextMethodVisitor = super.visitMethod(access, name, desc, signature, exceptions)

        return if (METHOD_NAME == name && METHOD_DESC == desc) {
            InstrumentationTargetMethodVisitor(
                api = api,
                methodVisitor = nextMethodVisitor,
                params = BytecodeMethodInsertionParams(
                    owner = "io/embrace/android/embracesdk/fcm/swazzle/callback/com/android/fcm/FirebaseSwazzledHooks",
                    name = "_onMessageReceived",
                    descriptor = "(Lcom/google/firebase/messaging/RemoteMessage;)V",
                    startVarIndex = 1,
                )
            )
        } else {
            nextMethodVisitor
        }
    }
}

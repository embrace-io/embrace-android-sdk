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
    private val logger: (() -> String) -> Unit
) : ClassVisitor(api, nextClassVisitor) { // TODO add tests

    companion object : ClassVisitFilter {
        private const val CLASS_NAME = "com.google.firebase.messaging.FirebaseMessagingService"
        private const val METHOD_NAME = "onMessageReceived"
        private const val METHOD_DESC = "(Lcom/google/firebase/messaging/RemoteMessage;)V"

        @Suppress("UnstableApiUsage")
        override fun accept(classContext: ClassContext): Boolean {
            if (classContext.currentClassData.superClasses.contains(CLASS_NAME)) {
                return true
            }
            return false
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

        return if (METHOD_NAME == name && METHOD_DESC == desc) {
            logger { "FirebaseMessagingServiceClassAdapter: instrumented method $name $desc" }
            FirebaseMessagingServiceMethodAdapter(api, nextMethodVisitor)
        } else {
            nextMethodVisitor
        }
    }
}

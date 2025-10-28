package io.embrace.android.embracesdk.internal.instrumentation.bytecode

import androidx.annotation.Keep
import com.google.firebase.messaging.RemoteMessage
import io.embrace.android.embracesdk.Embrace
import io.embrace.android.embracesdk.internal.EmbraceInternalApi

@Keep
object FcmBytecodeEntrypoint {

    @JvmStatic
    @Keep
    @Suppress("unused")
    fun onMessageReceived(message: RemoteMessage) {
        if (!Embrace.isStarted) {
            return
        }
        try {
            val notification: RemoteMessage.Notification? = message.notification

            Embrace.logPushNotification(
                notification?.title,
                notification?.body,
                message.from,
                message.messageId,
                notification?.notificationPriority,
                message.priority,
                notification != null,
                message.data.isNotEmpty()
            )
        } catch (e: Exception) {
            EmbraceInternalApi.internalInterface.logInternalError(e)
        }
    }
}

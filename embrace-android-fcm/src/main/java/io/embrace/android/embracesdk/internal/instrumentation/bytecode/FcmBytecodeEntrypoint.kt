package io.embrace.android.embracesdk.internal.instrumentation.bytecode

import androidx.annotation.Keep
import com.google.firebase.messaging.RemoteMessage
import io.embrace.android.embracesdk.Embrace
import io.embrace.android.embracesdk.internal.EmbraceInternalApi

@Keep
object FcmBytecodeEntrypoint {

    @JvmStatic
    @Keep
    fun onMessageReceived(message: RemoteMessage) {
        if (!Embrace.getInstance().isStarted) {
            return
        }
        try {
            val notification: RemoteMessage.Notification? = message.notification

            Embrace.getInstance().logPushNotification(
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
            EmbraceInternalApi.getInstance().internalInterface.logInternalError(e)
        }
    }
}

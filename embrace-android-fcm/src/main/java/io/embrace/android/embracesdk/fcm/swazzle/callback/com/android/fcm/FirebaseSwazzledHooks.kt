package io.embrace.android.embracesdk.fcm.swazzle.callback.com.android.fcm

import com.google.firebase.messaging.RemoteMessage
import io.embrace.android.embracesdk.Embrace
import io.embrace.android.embracesdk.internal.EmbraceInternalApi

object FirebaseSwazzledHooks {

    @JvmStatic
    fun _onMessageReceived(message: RemoteMessage) {
        if (!Embrace.getInstance().isStarted) {
            return
        }

        handleRemoteMessage(message)
    }

    private fun handleRemoteMessage(message: RemoteMessage) {
        try {
            var messageId: String? = null
            try {
                messageId = message.messageId
            } catch (e: Exception) {
                logError(e)
            }

            var topic: String? = null
            try {
                topic = message.from
            } catch (e: Exception) {
                logError(e)
            }

            var messagePriority: Int? = null
            try {
                messagePriority = message.priority
            } catch (e: Exception) {
                logError(e)
            }

            var notification: RemoteMessage.Notification? = null

            try {
                notification = message.notification
            } catch (e: Exception) {
                logError(e)
            }

            var title: String? = null
            var body: String? = null
            var notificationPriority: Int? = null
            if (notification != null) {
                try {
                    title = notification.title
                } catch (e: Exception) {
                    logError(e)
                }

                try {
                    body = notification.body
                } catch (e: Exception) {
                    logError(e)
                }

                try {
                    notificationPriority = notification.notificationPriority
                } catch (e: Exception) {
                    logError(e)
                }
            }

            val hasData = message.data.isNotEmpty()
            val hasNotification = notification != null

            try {
                Embrace.getInstance().logPushNotification(
                    title,
                    body,
                    topic,
                    messageId,
                    notificationPriority,
                    messagePriority,
                    hasNotification,
                    hasData
                )
            } catch (e: Exception) {
                logError(e)
            }
        } catch (e: Exception) {
            logError(e)
        }
    }

    private fun logError(e: Exception) {
        EmbraceInternalApi.getInstance().internalInterface.logInternalError(e)
    }
}

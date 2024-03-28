package io.embrace.android.embracesdk.fcm.swazzle.callback.com.android.fcm;

import static io.embrace.android.embracesdk.logging.InternalStaticEmbraceLogger.logger;

import androidx.annotation.NonNull;

import com.google.firebase.messaging.RemoteMessage;

import io.embrace.android.embracesdk.Embrace;
import io.embrace.android.embracesdk.annotation.InternalApi;

@InternalApi
public final class FirebaseSwazzledHooks {

    private FirebaseSwazzledHooks() {
    }

    @SuppressWarnings("MethodNameCheck")
    @InternalApi
    public static void _onMessageReceived(@NonNull RemoteMessage message) {
        logger.logDebug("Embrace received push notification message");

        if (!Embrace.getInstance().isStarted()) {
            logger.logError("Embrace received push notification data before the SDK was started");
            return;
        }

        handleRemoteMessage(message);
    }

    private static void handleRemoteMessage(@NonNull RemoteMessage message) {
        try {
            //flag process is already running to avoid track warm startup
            Embrace.getInstance().getInternalInterface().setProcessStartedByNotification();

            String messageId = null;
            try {
                messageId = message.getMessageId();
            } catch (Exception e) {
                logger.logError("Failed to capture FCM messageId", e);
            }

            String topic = null;
            try {
                topic = message.getFrom();
            } catch (Exception e) {
                logger.logError("Failed to capture FCM topic", e);
            }

            Integer messagePriority = null;
            try {
                messagePriority = message.getPriority();
            } catch (Exception e) {
                logger.logError("Failed to capture FCM message priority", e);
            }

            RemoteMessage.Notification notification = null;

            try {
                notification = message.getNotification();
            } catch (Exception e) {
                logger.logError("Failed to capture FCM RemoteMessage Notification", e);
            }

            String title = null;
            String body = null;
            Integer notificationPriority = null;
            if (notification != null) {
                try {
                    title = notification.getTitle();
                } catch (Exception e) {
                    logger.logError("Failed to capture FCM title", e);
                }

                try {
                    body = notification.getBody();
                } catch (Exception e) {
                    logger.logError("Failed to capture FCM body", e);
                }

                try {
                    notificationPriority = notification.getNotificationPriority();
                } catch (Exception e) {
                    logger.logError("Failed to capture FCM notificationPriority", e);
                }
            }

            Boolean hasData = !message.getData().isEmpty();
            Boolean hasNotification = notification != null;

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
                );
            } catch (Exception e) {
                logger.logError("Failed to log push Notification", e);
            }
        } catch (Exception e) {
            logger.logError("Push Notification Error", e);
        }
    }
}

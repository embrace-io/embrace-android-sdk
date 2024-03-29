package io.embrace.android.embracesdk.fcm.swazzle.callback.com.android.fcm;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

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
        if (!Embrace.getInstance().isStarted()) {
            logError("Embrace received push notification data before the SDK was started", null);
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
                logError("Failed to capture FCM messageId", e);
            }

            String topic = null;
            try {
                topic = message.getFrom();
            } catch (Exception e) {
                logError("Failed to capture FCM topic", e);
            }

            Integer messagePriority = null;
            try {
                messagePriority = message.getPriority();
            } catch (Exception e) {
                logError("Failed to capture FCM message priority", e);
            }

            RemoteMessage.Notification notification = null;

            try {
                notification = message.getNotification();
            } catch (Exception e) {
                logError("Failed to capture FCM RemoteMessage Notification", e);
            }

            String title = null;
            String body = null;
            Integer notificationPriority = null;
            if (notification != null) {
                try {
                    title = notification.getTitle();
                } catch (Exception e) {
                    logError("Failed to capture FCM title", e);
                }

                try {
                    body = notification.getBody();
                } catch (Exception e) {
                    logError("Failed to capture FCM body", e);
                }

                try {
                    notificationPriority = notification.getNotificationPriority();
                } catch (Exception e) {
                    logError("Failed to capture FCM notificationPriority", e);
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
                logError("Failed to log push Notification", e);
            }
        } catch (Exception e) {
            logError("Push Notification Error", e);
        }
    }

    private static void logError(@NonNull String message, @Nullable Exception e) {
        Embrace.getInstance().getInternalInterface().logError(message, null, null, false);
        if (e != null) {
            Embrace.getInstance().getInternalInterface().logInternalError(e);
        }
    }
}

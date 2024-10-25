package io.embrace.android.embracesdk.fcm.swazzle.callback.com.android.fcm;

import androidx.annotation.NonNull;

import com.google.firebase.messaging.RemoteMessage;

import io.embrace.android.embracesdk.Embrace;
import io.embrace.android.embracesdk.annotation.InternalApi;
import io.embrace.android.embracesdk.internal.EmbraceInternalApi;

@InternalApi
public final class FirebaseSwazzledHooks {

    private FirebaseSwazzledHooks() {
    }

    @SuppressWarnings("MethodNameCheck")
    @InternalApi
    public static void _onMessageReceived(@NonNull RemoteMessage message) {
        if (!Embrace.getInstance().isStarted()) {
            return;
        }

        handleRemoteMessage(message);
    }

    private static void handleRemoteMessage(@NonNull RemoteMessage message) {
        try {
            String messageId = null;
            try {
                messageId = message.getMessageId();
            } catch (Exception e) {
                logError(e);
            }

            String topic = null;
            try {
                topic = message.getFrom();
            } catch (Exception e) {
                logError(e);
            }

            Integer messagePriority = null;
            try {
                messagePriority = message.getPriority();
            } catch (Exception e) {
                logError(e);
            }

            RemoteMessage.Notification notification = null;

            try {
                notification = message.getNotification();
            } catch (Exception e) {
                logError(e);
            }

            String title = null;
            String body = null;
            Integer notificationPriority = null;
            if (notification != null) {
                try {
                    title = notification.getTitle();
                } catch (Exception e) {
                    logError(e);
                }

                try {
                    body = notification.getBody();
                } catch (Exception e) {
                    logError(e);
                }

                try {
                    notificationPriority = notification.getNotificationPriority();
                } catch (Exception e) {
                    logError(e);
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
                logError(e);
            }
        } catch (Exception e) {
            logError(e);
        }
    }

    private static void logError(@NonNull Exception e) {
        EmbraceInternalApi.getInstance().getInternalInterface().logInternalError(e);
    }
}

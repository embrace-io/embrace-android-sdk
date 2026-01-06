package io.embrace.android.embracesdk.internal.instrumentation.fcm

import com.google.firebase.messaging.RemoteMessage
import io.embrace.android.embracesdk.internal.arch.InstrumentationArgs
import io.embrace.android.embracesdk.internal.arch.datasource.DataSourceImpl
import io.embrace.android.embracesdk.internal.arch.limits.UpToLimitStrategy
import io.embrace.android.embracesdk.internal.arch.schema.SchemaType
import io.embrace.android.embracesdk.internal.instrumentation.fcm.PushNotificationBreadcrumb.NotificationType.Builder.notificationTypeFor
import io.embrace.android.embracesdk.internal.logging.InternalErrorType

/**
 * Captures custom breadcrumbs.
 */
class PushNotificationDataSource(
    args: InstrumentationArgs,
) : DataSourceImpl(
    args = args,
    limitStrategy = UpToLimitStrategy(args.configService.breadcrumbBehavior::getCustomBreadcrumbLimit),
    instrumentationName = "push_notification_data_source"
) {

    fun logPushNotification(
        message: RemoteMessage,
    ) {
        try {
            val notification: RemoteMessage.Notification? = message.notification
            val type = notificationTypeFor(message.data.isNotEmpty(), notification != null)

            logPushNotification(
                title = notification?.title,
                body = notification?.body,
                from = message.from,
                id = message.messageId,
                type = type,
                notificationPriority = notification?.notificationPriority,
            )
        } catch (e: Exception) {
            logger.trackInternalError(InternalErrorType.DATA_SOURCE_DATA_CAPTURE_FAIL, e)
        }
    }

    fun logPushNotification(
        title: String?,
        body: String?,
        from: String?,
        id: String?,
        notificationPriority: Int?,
        type: PushNotificationBreadcrumb.NotificationType,
    ) {
        captureTelemetry {
            val captureFcmPiiData = configService.breadcrumbBehavior.isFcmPiiDataCaptureEnabled()
            addSessionEvent(
                SchemaType.PushNotification(
                    title = if (captureFcmPiiData) title else null,
                    type = type.type,
                    body = if (captureFcmPiiData) body else null,
                    id = id ?: "",
                    from = if (captureFcmPiiData) from else null,
                    priority = notificationPriority ?: 0,
                ),
                clock.now()
            )
        }
    }
}

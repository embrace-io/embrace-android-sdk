package io.embrace.android.embracesdk.internal.capture.crumbs

import io.embrace.android.embracesdk.internal.arch.datasource.DataSourceImpl
import io.embrace.android.embracesdk.internal.arch.datasource.NoInputValidation
import io.embrace.android.embracesdk.internal.arch.destination.SessionSpanWriter
import io.embrace.android.embracesdk.internal.arch.limits.UpToLimitStrategy
import io.embrace.android.embracesdk.internal.arch.schema.SchemaType
import io.embrace.android.embracesdk.internal.clock.Clock
import io.embrace.android.embracesdk.internal.config.behavior.BreadcrumbBehavior
import io.embrace.android.embracesdk.internal.logging.EmbLogger
import io.embrace.android.embracesdk.internal.payload.PushNotificationBreadcrumb

/**
 * Captures custom breadcrumbs.
 */
class PushNotificationDataSource(
    private val breadcrumbBehavior: BreadcrumbBehavior,
    private val clock: Clock,
    writer: SessionSpanWriter,
    logger: EmbLogger
) : DataSourceImpl<SessionSpanWriter>(
    destination = writer,
    logger = logger,
    limitStrategy = UpToLimitStrategy(breadcrumbBehavior::getCustomBreadcrumbLimit)
) {

    fun logPushNotification(
        title: String?,
        body: String?,
        from: String?,
        id: String?,
        notificationPriority: Int?,
        type: PushNotificationBreadcrumb.NotificationType
    ) {
        captureData(
            inputValidation = NoInputValidation,
            captureAction = {
                val captureFcmPiiData = breadcrumbBehavior.isFcmPiiDataCaptureEnabled()
                addEvent(
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
        )
    }
}

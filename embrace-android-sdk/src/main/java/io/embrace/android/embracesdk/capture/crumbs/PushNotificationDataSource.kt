package io.embrace.android.embracesdk.capture.crumbs

import io.embrace.android.embracesdk.arch.datasource.DataSourceImpl
import io.embrace.android.embracesdk.arch.datasource.NoInputValidation
import io.embrace.android.embracesdk.arch.destination.SessionSpanWriter
import io.embrace.android.embracesdk.arch.limits.UpToLimitStrategy
import io.embrace.android.embracesdk.arch.schema.SchemaType
import io.embrace.android.embracesdk.config.behavior.BreadcrumbBehavior
import io.embrace.android.embracesdk.internal.clock.Clock
import io.embrace.android.embracesdk.logging.EmbLogger
import io.embrace.android.embracesdk.payload.PushNotificationBreadcrumb

/**
 * Captures custom breadcrumbs.
 */
internal class PushNotificationDataSource(
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
        alterSessionSpan(
            inputValidation = NoInputValidation,
            captureAction = {
                val captureFcmPiiData = breadcrumbBehavior.isCaptureFcmPiiDataEnabled()
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

package io.embrace.android.embracesdk.capture.crumbs

import io.embrace.android.embracesdk.arch.datasource.DataSourceImpl
import io.embrace.android.embracesdk.arch.destination.SessionSpanWriter
import io.embrace.android.embracesdk.arch.destination.SpanEventData
import io.embrace.android.embracesdk.arch.destination.SpanEventMapper
import io.embrace.android.embracesdk.arch.limits.UpToLimitStrategy
import io.embrace.android.embracesdk.arch.schema.SchemaType
import io.embrace.android.embracesdk.config.behavior.BreadcrumbBehavior
import io.embrace.android.embracesdk.internal.clock.Clock
import io.embrace.android.embracesdk.internal.clock.millisToNanos
import io.embrace.android.embracesdk.logging.InternalEmbraceLogger
import io.embrace.android.embracesdk.payload.PushNotificationBreadcrumb

/**
 * Captures custom breadcrumbs.
 */
internal class PushNotificationDataSource(
    private val breadcrumbBehavior: BreadcrumbBehavior,
    private val clock: Clock,
    writer: SessionSpanWriter,
    private val logger: InternalEmbraceLogger
) : DataSourceImpl<SessionSpanWriter>(
    destination = writer,
    logger = logger,
    limitStrategy = UpToLimitStrategy(logger, breadcrumbBehavior::getCustomBreadcrumbLimit)
),
    SpanEventMapper<PushNotificationBreadcrumb> {

    fun logPushNotification(
        title: String?,
        body: String?,
        from: String?,
        id: String?,
        notificationPriority: Int?,
        type: PushNotificationBreadcrumb.NotificationType
    ) {
        try {
            alterSessionSpan(
                inputValidation = {
                    true
                },
                captureAction = {
                    val captureFcmPiiData = breadcrumbBehavior.isCaptureFcmPiiDataEnabled()
                    val crumb = PushNotificationBreadcrumb(
                        if (captureFcmPiiData) title else null,
                        if (captureFcmPiiData) body else null,
                        if (captureFcmPiiData) from else null,
                        id,
                        notificationPriority,
                        type.type,
                        clock.now()
                    )
                    addEvent(crumb, ::toSpanEventData)
                }
            )
        } catch (ex: Exception) {
            logger.logError("Failed to log push notificaction $id", ex)
        }
    }

    override fun toSpanEventData(obj: PushNotificationBreadcrumb): SpanEventData {
        return SpanEventData(
            SchemaType.PushNotification(
                obj.title ?: "",
                obj.type,
                obj.body ?: "",
                obj.id ?: "",
                obj.from ?: "",
                obj.priority ?: 0
            ),
            obj.timestamp.millisToNanos()
        )
    }
}

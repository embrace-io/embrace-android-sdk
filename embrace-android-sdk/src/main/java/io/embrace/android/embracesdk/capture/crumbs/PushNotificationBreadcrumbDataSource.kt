package io.embrace.android.embracesdk.capture.crumbs

import io.embrace.android.embracesdk.arch.DataCaptureService
import io.embrace.android.embracesdk.config.ConfigService
import io.embrace.android.embracesdk.internal.clock.Clock
import io.embrace.android.embracesdk.logging.InternalEmbraceLogger
import io.embrace.android.embracesdk.logging.InternalStaticEmbraceLogger
import io.embrace.android.embracesdk.payload.PushNotificationBreadcrumb

/**
 * Captures push notification breadcrumbs.
 */
internal class PushNotificationBreadcrumbDataSource(
    private val configService: ConfigService,
    private val clock: Clock,
    private val store: BreadcrumbDataStore<PushNotificationBreadcrumb> = BreadcrumbDataStore {
        configService.breadcrumbBehavior.getCustomBreadcrumbLimit()
    },
    private val logger: InternalEmbraceLogger = InternalStaticEmbraceLogger.logger
) : DataCaptureService<List<PushNotificationBreadcrumb>> by store {

    fun logPushNotification(
        title: String?,
        body: String?,
        topic: String?,
        id: String?,
        notificationPriority: Int?,
        type: PushNotificationBreadcrumb.NotificationType
    ) {
        try {
            val captureFcmPiiData = configService.breadcrumbBehavior.isCaptureFcmPiiDataEnabled()
            val pn = PushNotificationBreadcrumb(
                if (captureFcmPiiData) title else null,
                if (captureFcmPiiData) body else null,
                if (captureFcmPiiData) topic else null,
                id,
                notificationPriority,
                type.type,
                clock.now()
            )
            store.tryAddBreadcrumb(pn)
        } catch (ex: Exception) {
            logger.logError("Failed to capture push notification", ex)
        }
    }
}

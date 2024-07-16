package io.embrace.android.embracesdk.internal.capture.crumbs

import android.app.Activity
import io.embrace.android.embracesdk.internal.clock.Clock
import io.embrace.android.embracesdk.internal.config.ConfigService
import io.embrace.android.embracesdk.internal.injection.DataSourceModule
import io.embrace.android.embracesdk.internal.session.lifecycle.ActivityLifecycleListener
import io.embrace.android.embracesdk.internal.utils.Provider
import io.embrace.android.embracesdk.payload.PushNotificationBreadcrumb
import io.embrace.android.embracesdk.payload.TapBreadcrumb

/**
 * Handles the logging of breadcrumbs.
 *
 * Breadcrumbs record a user's journey through the app and are split into:
 *
 *  * View breadcrumbs: Each time the user changes view in the app
 *  * Tap breadcrumbs: Each time the user taps a UI element in the app
 *  * Custom breadcrumbs: User-defined interactions within the app
 *
 * Breadcrumbs are limited at query-time by default to 100 per session, but this can be overridden
 * in server-side configuration. They are stored in an unbounded queue.
 */
internal class EmbraceBreadcrumbService(
    private val clock: Clock,
    private val configService: ConfigService,
    private val dataSourceModuleProvider: Provider<DataSourceModule?>
) : BreadcrumbService, ActivityLifecycleListener {

    override fun logView(screen: String?, timestamp: Long) {
        dataSourceModuleProvider()?.viewDataSource?.dataSource?.changeView(screen)
    }

    override fun startView(name: String?): Boolean {
        return dataSourceModuleProvider()?.viewDataSource?.dataSource?.startView(name) ?: false
    }

    override fun endView(name: String?): Boolean {
        return dataSourceModuleProvider()?.viewDataSource?.dataSource?.endView(name) ?: false
    }

    override fun logTap(
        point: Pair<Float?, Float?>,
        element: String,
        timestamp: Long,
        type: TapBreadcrumb.TapBreadcrumbType
    ) {
        dataSourceModuleProvider()?.tapDataSource?.dataSource?.apply {
            logTap(point, element, timestamp, type)
        }
    }

    override fun logCustom(message: String, timestamp: Long) {
        dataSourceModuleProvider()?.breadcrumbDataSource?.dataSource?.apply {
            logCustom(message, timestamp)
        }
    }

    override fun logRnAction(
        name: String,
        startTime: Long,
        endTime: Long,
        properties: Map<String?, Any?>,
        bytesSent: Int,
        output: String
    ) {
        dataSourceModuleProvider()?.rnActionDataSource?.dataSource?.apply {
            this.logRnAction(name, startTime, endTime, properties, bytesSent, output)
        }
    }

    override fun logWebView(url: String?, startTime: Long) {
        dataSourceModuleProvider()?.webViewUrlDataSource?.dataSource?.apply {
            logWebView(url, startTime)
        }
    }

    override fun logPushNotification(
        title: String?,
        body: String?,
        topic: String?,
        id: String?,
        notificationPriority: Int?,
        messageDeliveredPriority: Int,
        type: PushNotificationBreadcrumb.NotificationType
    ) {
        dataSourceModuleProvider()?.pushNotificationDataSource?.dataSource?.apply {
            logPushNotification(
                title,
                body,
                topic,
                id,
                notificationPriority,
                type
            )
        }
    }

    override fun onView(activity: Activity) {
        if (configService.breadcrumbBehavior.isAutomaticActivityCaptureEnabled()) {
            logView(activity.javaClass.name, clock.now())
        }
    }

    /**
     * Close all open fragments when the activity closes
     */
    override fun onViewClose(activity: Activity) {
        if (configService.breadcrumbBehavior.isAutomaticActivityCaptureEnabled()) {
            dataSourceModuleProvider()?.viewDataSource?.dataSource?.onViewClose()
        }
    }
}

package io.embrace.android.embracesdk.capture.crumbs

import android.app.Activity
import android.util.Pair
import io.embrace.android.embracesdk.arch.destination.SessionSpanWriter
import io.embrace.android.embracesdk.config.ConfigService
import io.embrace.android.embracesdk.internal.clock.Clock
import io.embrace.android.embracesdk.internal.spans.SpanService
import io.embrace.android.embracesdk.logging.InternalEmbraceLogger
import io.embrace.android.embracesdk.logging.InternalStaticEmbraceLogger
import io.embrace.android.embracesdk.payload.Breadcrumbs
import io.embrace.android.embracesdk.payload.PushNotificationBreadcrumb.NotificationType
import io.embrace.android.embracesdk.payload.TapBreadcrumb.TapBreadcrumbType
import io.embrace.android.embracesdk.session.MemoryCleanerListener
import io.embrace.android.embracesdk.session.lifecycle.ActivityLifecycleListener
import io.embrace.android.embracesdk.session.lifecycle.ActivityTracker

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
    private val activityTracker: ActivityTracker,
    sessionSpanWriter: SessionSpanWriter,
    spanService: SpanService,
    private val logger: InternalEmbraceLogger = InternalStaticEmbraceLogger.logger
) : BreadcrumbService, ActivityLifecycleListener, MemoryCleanerListener {

    private val customBreadcrumbDataSource =
        CustomBreadcrumbDataSource(configService.breadcrumbBehavior, sessionSpanWriter)
    private val webViewBreadcrumbDataSource = WebViewBreadcrumbDataSource(configService)
    private val rnBreadcrumbDataSource = RnBreadcrumbDataSource(configService)
    private val tapBreadcrumbDataSource = TapBreadcrumbDataSource(configService)
    private val viewBreadcrumbDataSource = ViewBreadcrumbDataSource(configService, clock)
    private val fragmentBreadcrumbDataSource = FragmentBreadcrumbDataSource(
        configService.breadcrumbBehavior,
        clock,
        spanService
    )
    private val pushNotificationBreadcrumbDataSource =
        PushNotificationBreadcrumbDataSource(configService, clock)

    override fun logView(screen: String?, timestamp: Long) {
        viewBreadcrumbDataSource.addToViewLogsQueue(screen, timestamp, false)
    }

    override fun forceLogView(screen: String?, timestamp: Long) {
        viewBreadcrumbDataSource.addToViewLogsQueue(screen, timestamp, true)
    }

    override fun startView(name: String?): Boolean {
        return fragmentBreadcrumbDataSource.startFragment(name)
    }

    override fun endView(name: String?): Boolean {
        return fragmentBreadcrumbDataSource.endFragment(name)
    }

    override fun logTap(
        point: Pair<Float?, Float?>,
        element: String,
        timestamp: Long,
        type: TapBreadcrumbType
    ) {
        tapBreadcrumbDataSource.logTap(point, element, timestamp, type)
    }

    override fun logCustom(message: String, timestamp: Long) {
        customBreadcrumbDataSource.logCustom(message, timestamp)
    }

    override fun logRnAction(
        name: String,
        startTime: Long,
        endTime: Long,
        properties: Map<String?, Any?>,
        bytesSent: Int,
        output: String
    ) {
        rnBreadcrumbDataSource.logRnAction(name, startTime, endTime, properties, bytesSent, output)
    }

    override fun logWebView(url: String?, startTime: Long) {
        webViewBreadcrumbDataSource.logWebView(url, startTime)
    }

    override fun getBreadcrumbs() = Breadcrumbs(
        tapBreadcrumbs = tapBreadcrumbDataSource.getCapturedData(),
        viewBreadcrumbs = viewBreadcrumbDataSource.getCapturedData(),
        webViewBreadcrumbs = webViewBreadcrumbDataSource.getCapturedData(),
        rnActionBreadcrumbs = rnBreadcrumbDataSource.getCapturedData(),
        pushNotifications = pushNotificationBreadcrumbDataSource.getCapturedData()
    )

    override fun flushBreadcrumbs(): Breadcrumbs {
        val breadcrumbs = getBreadcrumbs()
        cleanCollections()
        return breadcrumbs
    }

    override fun logPushNotification(
        title: String?,
        body: String?,
        topic: String?,
        id: String?,
        notificationPriority: Int?,
        messageDeliveredPriority: Int,
        type: NotificationType
    ) = pushNotificationBreadcrumbDataSource.logPushNotification(
        title,
        body,
        topic,
        id,
        notificationPriority,
        type
    )

    override fun onView(activity: Activity) {
        if (configService.breadcrumbBehavior.isActivityBreadcrumbCaptureEnabled()) {
            logView(activity.javaClass.name, clock.now())
        }
    }

    /**
     * Close all open fragments when the activity closes
     */
    override fun onViewClose(activity: Activity) {
        if (!configService.breadcrumbBehavior.isActivityBreadcrumbCaptureEnabled()) {
            return
        }
        viewBreadcrumbDataSource.onViewClose()
        fragmentBreadcrumbDataSource.onViewClose()
    }

    override fun cleanCollections() {
        viewBreadcrumbDataSource.cleanCollections()
        tapBreadcrumbDataSource.cleanCollections()
        webViewBreadcrumbDataSource.cleanCollections()
        pushNotificationBreadcrumbDataSource.cleanCollections()
        rnBreadcrumbDataSource.cleanCollections()
    }

    override fun addFirstViewBreadcrumbForSession(startTime: Long) {
        val screen: String? = getLastViewBreadcrumbScreenName()
        if (screen != null) {
            replaceFirstSessionView(screen, startTime)
        } else {
            val foregroundActivity = activityTracker.foregroundActivity
            if (foregroundActivity != null) {
                forceLogView(
                    foregroundActivity.localClassName,
                    startTime
                )
            }
        }
    }

    override fun replaceFirstSessionView(screen: String, timestamp: Long) {
        viewBreadcrumbDataSource.replaceFirstSessionView(screen, timestamp)
    }

    override fun getLastViewBreadcrumbScreenName(): String? =
        viewBreadcrumbDataSource.getLastViewBreadcrumbScreenName()
}

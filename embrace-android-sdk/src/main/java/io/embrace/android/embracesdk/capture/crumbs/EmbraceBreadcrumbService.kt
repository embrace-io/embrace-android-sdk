package io.embrace.android.embracesdk.capture.crumbs

import android.app.Activity
import android.util.Pair
import io.embrace.android.embracesdk.config.ConfigService
import io.embrace.android.embracesdk.internal.ApkToolsConfig
import io.embrace.android.embracesdk.internal.CacheableValue
import io.embrace.android.embracesdk.internal.clock.Clock
import io.embrace.android.embracesdk.logging.InternalEmbraceLogger
import io.embrace.android.embracesdk.logging.InternalStaticEmbraceLogger
import io.embrace.android.embracesdk.payload.Breadcrumbs
import io.embrace.android.embracesdk.payload.CustomBreadcrumb
import io.embrace.android.embracesdk.payload.FragmentBreadcrumb
import io.embrace.android.embracesdk.payload.PushNotificationBreadcrumb
import io.embrace.android.embracesdk.payload.PushNotificationBreadcrumb.NotificationType
import io.embrace.android.embracesdk.payload.RnActionBreadcrumb
import io.embrace.android.embracesdk.payload.TapBreadcrumb
import io.embrace.android.embracesdk.payload.TapBreadcrumb.TapBreadcrumbType
import io.embrace.android.embracesdk.payload.ViewBreadcrumb
import io.embrace.android.embracesdk.payload.WebViewBreadcrumb
import io.embrace.android.embracesdk.session.MemoryCleanerListener
import io.embrace.android.embracesdk.session.lifecycle.ActivityLifecycleListener
import io.embrace.android.embracesdk.session.lifecycle.ActivityTracker
import io.embrace.android.embracesdk.utils.filter
import java.util.Collections
import java.util.Deque
import java.util.concurrent.LinkedBlockingDeque

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
    private val logger: InternalEmbraceLogger = InternalStaticEmbraceLogger.logger
) : BreadcrumbService, ActivityLifecycleListener, MemoryCleanerListener {

    /**
     * A deque of breadcrumbs.
     */
    private val viewBreadcrumbs = LinkedBlockingDeque<ViewBreadcrumb?>()
    private val customBreadcrumbDataSource = CustomBreadcrumbDataSource(configService)
    private val webViewBreadcrumbDataSource = WebViewBreadcrumbDataSource(configService)
    private val rnBreadcrumbDataSource = RnBreadcrumbDataSource(configService)
    private val tapBreadcrumbDataSource = TapBreadcrumbDataSource(configService)
    private val pushNotificationBreadcrumbDataSource =
        PushNotificationBreadcrumbDataSource(configService, clock)

    val fragmentBreadcrumbs = LinkedBlockingDeque<FragmentBreadcrumb?>()
    val fragmentStack = Collections.synchronizedList(ArrayList<FragmentBreadcrumb>())
    private val viewBreadcrumbsCache: CacheableValue<List<ViewBreadcrumb?>> =
        CacheableValue { isCacheValid(viewBreadcrumbs) }
    private val fragmentsCache: CacheableValue<List<FragmentBreadcrumb?>> =
        CacheableValue { isCacheValid(fragmentBreadcrumbs) }

    override fun logView(screen: String?, timestamp: Long) {
        if (ApkToolsConfig.IS_BREADCRUMB_TRACKING_DISABLED) {
            return
        }
        logger.logDeveloper("EmbraceBreadcrumbsService", "logView")
        addToViewLogsQueue(screen, timestamp, false)
    }

    override fun forceLogView(screen: String?, timestamp: Long) {
        if (ApkToolsConfig.IS_BREADCRUMB_TRACKING_DISABLED) {
            return
        }
        logger.logDeveloper("EmbraceBreadcrumbsService", "forceLogView")
        addToViewLogsQueue(screen, timestamp, true)
    }

    @Synchronized
    override fun replaceFirstSessionView(screen: String?, timestamp: Long) {
        if (ApkToolsConfig.IS_BREADCRUMB_TRACKING_DISABLED) {
            return
        }
        logger.logDeveloper("EmbraceBreadcrumbsService", "replaceFirstSessionView")
        viewBreadcrumbs.removeLast()
        val limit = configService.breadcrumbBehavior.getViewBreadcrumbLimit()
        tryAddBreadcrumb(viewBreadcrumbs, ViewBreadcrumb(screen, timestamp), limit)
    }

    override fun startView(name: String?): Boolean {
        if (ApkToolsConfig.IS_BREADCRUMB_TRACKING_DISABLED || name == null) {
            return false
        }
        logger.logDeveloper("EmbraceBreadcrumbsService", "Starting view: $name")
        synchronized(this) {
            if (fragmentStack.size >= DEFAULT_VIEW_STACK_SIZE) {
                val msg =
                    "Cannot add view, view stack exceed the limit of " + DEFAULT_VIEW_STACK_SIZE
                logger.logDeveloper("EmbraceBreadcrumbsService", msg)
                return false
            }
            logger.logDeveloper("EmbraceBreadcrumbsService", "View added: $name")
            return fragmentStack.add(FragmentBreadcrumb(name, clock.now(), 0))
        }
    }

    override fun endView(name: String?): Boolean {
        if (ApkToolsConfig.IS_BREADCRUMB_TRACKING_DISABLED || name == null) {
            return false
        }
        logger.logDeveloper("EmbraceBreadcrumbsService", "Ending view: $name")
        var start: FragmentBreadcrumb
        val end = FragmentBreadcrumb(name, 0, clock.now())
        synchronized(this) {
            val crumbs = filter(fragmentStack) { crumb: FragmentBreadcrumb -> crumb.name == name }
            if (crumbs.isEmpty()) {
                logger.logDeveloper("EmbraceBreadcrumbsService", "Cannot end view")
                return false
            }
            start = crumbs[0]
            fragmentStack.remove(start)
        }
        end.setStartTime(start.getStartTime())
        logger.logDeveloper("EmbraceBreadcrumbsService", "View ended")
        val limit = configService.breadcrumbBehavior.getFragmentBreadcrumbLimit()
        tryAddBreadcrumb(fragmentBreadcrumbs, end, limit)
        return true
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

    override fun getViewBreadcrumbsForSession(
        start: Long,
        end: Long
    ): List<ViewBreadcrumb?> {
        return viewBreadcrumbsCache.value {
            filterBreadcrumbsForTimeWindow(
                viewBreadcrumbs,
                start,
                end
            )
        }
    }

    override fun getTapBreadcrumbsForSession(): List<TapBreadcrumb> {
        return tapBreadcrumbDataSource.getCapturedData()
    }

    override fun getCustomBreadcrumbsForSession(): List<CustomBreadcrumb> {
        return customBreadcrumbDataSource.getCapturedData()
    }

    override fun getRnActionBreadcrumbForSession(): List<RnActionBreadcrumb> {
        return rnBreadcrumbDataSource.getCapturedData()
    }

    override fun getWebViewBreadcrumbsForSession(): List<WebViewBreadcrumb> {
        return webViewBreadcrumbDataSource.getCapturedData()
    }

    override fun getFragmentBreadcrumbsForSession(
        startTime: Long,
        endTime: Long
    ): List<FragmentBreadcrumb?> {
        return fragmentsCache.value {
            filterBreadcrumbsForTimeWindow(
                fragmentBreadcrumbs,
                startTime,
                endTime
            )
        }
    }

    override fun getPushNotificationsBreadcrumbsForSession(): List<PushNotificationBreadcrumb> {
        return pushNotificationBreadcrumbDataSource.getCapturedData()
    }

    override fun getBreadcrumbs(start: Long, end: Long): Breadcrumbs {
        return Breadcrumbs(
            customBreadcrumbs = getCustomBreadcrumbsForSession(),
            tapBreadcrumbs = getTapBreadcrumbsForSession(),
            viewBreadcrumbs = getViewBreadcrumbsForSession(start, end).filterNotNull(),
            webViewBreadcrumbs = getWebViewBreadcrumbsForSession(),
            fragmentBreadcrumbs = getFragmentBreadcrumbsForSession(start, end).filterNotNull(),
            rnActionBreadcrumbs = getRnActionBreadcrumbForSession(),
            pushNotifications = getPushNotificationsBreadcrumbsForSession()
        )
    }

    override fun flushBreadcrumbs(): Breadcrumbs {
        // given that start and end are ignored because of the cache, we can just pass 0
        val breadcrumbs = getBreadcrumbs(0, clock.now())
        cleanCollections()
        return breadcrumbs
    }

    private fun <T> isCacheValid(collection: Collection<T>): Int {
        val last = collection.lastOrNull()
        val code = last?.hashCode() ?: 0
        return collection.size + code
    }

    override fun getLastViewBreadcrumbScreenName(): String? {
        if (viewBreadcrumbs.isEmpty()) {
            logger.logDeveloper("EmbraceBreadcrumbsService", "View breadcrumb stack is empty")
        } else {
            val crumb = viewBreadcrumbs.peek()
            if (crumb != null) {
                val lastViewBreadcrumb = crumb.screen
                logger.logDeveloper(
                    "EmbraceBreadcrumbsService",
                    "Last  view breadcrumb is: $lastViewBreadcrumb"
                )
                return lastViewBreadcrumb
            }
        }
        return null
    }

    override fun logPushNotification(
        title: String?,
        body: String?,
        topic: String?,
        id: String?,
        notificationPriority: Int?,
        messageDeliveredPriority: Int,
        type: NotificationType
    ) {
        pushNotificationBreadcrumbDataSource.logPushNotification(
            title,
            body,
            topic,
            id,
            notificationPriority,
            type
        )
    }

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
        try {
            val lastViewBreadcrumb = viewBreadcrumbs.peek()
            if (lastViewBreadcrumb != null) {
                lastViewBreadcrumb.end = clock.now()
                logger.logDeveloper(
                    "EmbraceBreadcrumbsService",
                    "End set for breadcrumb $lastViewBreadcrumb"
                )
            } else {
                logger.logDeveloper("EmbraceBreadcrumbsService", "There are no breadcrumbs to end")
            }
        } catch (ex: Exception) {
            logger.logDebug("Failed to add set end time for breadcrumb", ex)
        }
        if (fragmentStack.size == 0) {
            logger.logDeveloper(
                "EmbraceBreadcrumbsService",
                "There are no breadcrumbs fragments to clear"
            )
            return
        }
        val ts = clock.now()
        synchronized(fragmentStack) {
            logger.logDeveloper("EmbraceBreadcrumbsService", "Ending breadcrumb fragments")
            for (fragment in fragmentStack) {
                fragment.endTime = ts
                val limit = configService.breadcrumbBehavior.getFragmentBreadcrumbLimit()
                tryAddBreadcrumb(fragmentBreadcrumbs, fragment, limit)
            }
            fragmentStack.clear()
        }
    }

    override fun cleanCollections() {
        viewBreadcrumbs.clear()
        tapBreadcrumbDataSource.cleanCollections()
        customBreadcrumbDataSource.cleanCollections()
        webViewBreadcrumbDataSource.cleanCollections()
        fragmentBreadcrumbs.clear()
        fragmentStack.clear()
        pushNotificationBreadcrumbDataSource.cleanCollections()
        rnBreadcrumbDataSource.cleanCollections()
        logger.logDeveloper("EmbraceBreadcrumbsService", "Collections cleaned")
    }

    /**
     * Adds the view breadcrumb to the queue.
     *
     * @param screen    name of the screen.
     * @param timestamp time of occurrence of the tap event.
     * @param force     will run no duplication checks on the previous view breadcrumb registry.
     */
    @Synchronized
    private fun addToViewLogsQueue(screen: String?, timestamp: Long, force: Boolean) {
        try {
            val lastViewBreadcrumb = viewBreadcrumbs.peek()
            val lastScreen = lastViewBreadcrumb?.screen ?: ""
            if (force || lastViewBreadcrumb == null || !lastScreen.equals(
                    screen.toString(),
                    ignoreCase = true
                )
            ) {
                // TODO: is `lastViewBreadcrumb` a copy or the actual object in the queue?
                if (lastViewBreadcrumb != null) {
                    logger.logDeveloper(
                        "EmbraceBreadcrumbsService",
                        "Ending lastViewBreadcrumb to add another"
                    )
                    lastViewBreadcrumb.end = timestamp
                }
                val limit = configService.breadcrumbBehavior.getViewBreadcrumbLimit()
                tryAddBreadcrumb(viewBreadcrumbs, ViewBreadcrumb(screen, timestamp), limit)
            }
        } catch (ex: Exception) {
            logger.logError("Failed to add view breadcrumb for $screen", ex)
        }
    }

    /**
     * Returns the latest breadcrumbs within the specified interval, up to the maximum queue size or
     * configured limit in the app configuration.
     *
     * @param breadcrumbs breadcrumbs list to filter.
     * @param startTime   beginning of the time window.
     * @param endTime     end of the time window.
     * @return filtered breadcrumbs from the provided FixedSizeDeque.
     */
    private fun <T : Breadcrumb?> filterBreadcrumbsForTimeWindow(
        breadcrumbs: Deque<T>,
        startTime: Long,
        endTime: Long
    ): List<T> {
        logger.logDeveloper("EmbraceBreadcrumbsService", "Filtering breadcrumbs for time window")
        return filter(breadcrumbs) { crumb: T ->
            checkNotNull(crumb)
            crumb.getStartTime() >= startTime && (endTime <= 0L || crumb.getStartTime() <= endTime)
        }
    }

    private fun <T> tryAddBreadcrumb(
        breadcrumbs: LinkedBlockingDeque<T>,
        breadcrumb: T,
        limit: Int
    ) {
        if (!breadcrumbs.isEmpty() && breadcrumbs.size >= limit) {
            breadcrumbs.removeLast()
            logger.logDeveloper("EmbraceBreadcrumbsService", "removed last breadcrumb from stack")
        }
        breadcrumbs.push(breadcrumb)
        logger.logDeveloper("EmbraceBreadcrumbsService", "added breadcrumb")
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

    companion object {

        /**
         * The default limit for how many open tracked fragments are allowed, which can be overridden
         * by [RemoteConfig].
         */
        private const val DEFAULT_VIEW_STACK_SIZE = 20
    }
}

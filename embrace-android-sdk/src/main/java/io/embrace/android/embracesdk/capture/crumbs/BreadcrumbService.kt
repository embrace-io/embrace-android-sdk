package io.embrace.android.embracesdk.capture.crumbs

import android.util.Pair
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

/**
 * Service which stores breadcrumbs for the application.
 */
internal interface BreadcrumbService {

    /**
     * Gets the view breadcrumbs in the specified time window.
     * If the number of elements exceeds the limit, this will return the newest (latest) ones.
     *
     * @param start the start time
     * @param end   the end time
     * @return the list of Breadcrumbs
     */
    fun getViewBreadcrumbsForSession(start: Long, end: Long): List<ViewBreadcrumb?>

    /**
     * Gets the Taps breadcrumbs in the specified time window.
     * If the number of elements exceeds the limit, this will return the newest (latest) ones.
     *
     * @return the list of Breadcrumbs
     */
    fun getTapBreadcrumbsForSession(): List<TapBreadcrumb>

    /**
     * Gets the Custom breadcrumbs in the specified time window.
     * If the number of elements exceeds the limit, this will return the newest (latest) ones.
     *
     * @return the list of Breadcrumbs
     */
    fun getCustomBreadcrumbsForSession(): List<CustomBreadcrumb>

    /**
     * Gets the WebView breadcrumbs in the specified time window.
     * If the number of elements exceeds the limit, this will return the newest (latest) ones.
     *
     * @return the list of Breadcrumbs
     */
    fun getWebViewBreadcrumbsForSession(): List<WebViewBreadcrumb>

    /**
     * Gets the WebView breadcrumbs in the specified time window.
     * If the number of elements exceeds the limit, this will return the newest (latest) ones.
     *
     * @param startTime the start time
     * @param endTime   the end time
     * @return the list of Breadcrumbs
     */
    fun getFragmentBreadcrumbsForSession(startTime: Long, endTime: Long): List<FragmentBreadcrumb?>

    /**
     * Gets the RN Actions breadcrumbs in the specified time window.
     * If the number of elements exceeds the limit, this will return the newest (latest) ones.
     *
     * @return the list of Breadcrumbs
     */
    fun getRnActionBreadcrumbForSession(): List<RnActionBreadcrumb>

    /**
     * Gets the captured Push Notifications breadcrumbs in the specified time window.
     * If the number of elements exceeds the limit, this will return the newest (latest) ones.
     *
     * @param startTime the start time
     * @param endTime   the end time
     * @return the list of Breadcrumbs
     */
    fun getPushNotificationsBreadcrumbsForSession(): List<PushNotificationBreadcrumb>

    /**
     * Gets all breadcrumbs within the specified time window. If the number of elements exceeds the
     * limit for each breadcrumb type, only the latest will be returned.
     *
     * @param start the start time
     * @param end   the end time
     * @return the breadcrumbs
     */
    fun getBreadcrumbs(start: Long, end: Long): Breadcrumbs

    /**
     * Gets all breacrumbs and clear the lists
     *
     * @return the breadcrumbs
     */
    fun flushBreadcrumbs(): Breadcrumbs

    /**
     * Registers a view breadcrumb.
     * The view breadcrumb will not be registered if the last view breadcrumb registry has the same
     * screen name.
     *
     * @param screen    name of the screen.
     * @param timestamp time of occurrence of the tap event.
     */
    fun logView(screen: String?, timestamp: Long)

    /**
     * Unlike [EmbraceBreadcrumbService.logView]
     * this function logs the view despite the previous one in the queue has the same screen name.
     *
     * @param screen    name of the screen.
     * @param timestamp time of occurrence of the tap event.
     */
    fun forceLogView(screen: String?, timestamp: Long)

    /**
     * this function replaces the first session view in order to have it the
     * scope time of the session.
     *
     * @param timestamp time to set as beginning of the view.
     */
    fun replaceFirstSessionView(screen: String?, timestamp: Long)

    /**
     * Logs the start of a view. Must be matched by a call to
     * [EmbraceBreadcrumbService.endView].
     *
     * @param name name of the view.
     */
    fun startView(name: String?): Boolean

    /**
     * Logs the end of a view. A call to
     * [EmbraceBreadcrumbService.startView] must have been made before this
     * call is made.
     *
     * @param name name of the view.
     */
    fun endView(name: String?): Boolean

    /**
     * Registers a tap event as a breadcrumb.
     *
     * @param point     coordinates of the tapped element.
     * @param element   tapped element view.
     * @param timestamp time of occurrence of the tap event.
     * @param type      type of tap event
     */
    fun logTap(
        point: Pair<Float?, Float?>,
        element: String,
        timestamp: Long,
        type: TapBreadcrumbType
    )

    /**
     * Registers a custom event as a breadcrumb.
     *
     * @param message message for the custom breadcrumb.
     */
    fun logCustom(message: String, timestamp: Long)

    /**
     * Registers a RN Action as a breadcrumb.
     *
     * @param name       The Action name.
     * @param startTime  The Action start time.
     * @param endTime    The Action end time.
     * @param properties Extra properties that are not covered in the others.
     * @param output     The supported values are SUCCESS, FAIL and INCOMPLETE
     */
    fun logRnAction(
        name: String,
        startTime: Long,
        endTime: Long,
        properties: Map<String?, Any?>,
        bytesSent: Int,
        output: String
    )

    /**
     * Registers a WebView breadcrumb.
     *
     * @param url       the URL navigated to.
     * @param startTime the start time of the web view
     */
    fun logWebView(url: String?, startTime: Long)

    /**
     * This function can be used to recover the last view breadcrumb registered.
     *
     * @return the last registered view breadcrumb in the queue.
     */
    fun getLastViewBreadcrumbScreenName(): String?

    /**
     * Saves captured push notification information into session payload
     *
     * @param title                    the title of the notification as a string (or null)
     * @param body                     the body of the notification as a string (or null)
     * @param topic                    the notification topic (if a user subscribed to one), or null
     * @param id                       A unique ID identifying the message
     * @param notificationPriority     the notificationPriority of the message (as resolved on the device)
     * @param messageDeliveredPriority the delivered priority of the message (as resolved on the server)
     * @param type                     the notification type
     */
    fun logPushNotification(
        title: String?,
        body: String?,
        topic: String?,
        id: String?,
        notificationPriority: Int?,
        messageDeliveredPriority: Int,
        type: NotificationType
    )

    /**
     * This function adds the current view breadcrumb if the app comes from background to foreground
     * or replaces the first session view breadcrumb possibly created before the session in order to
     * have it in the session scope time.
     */
    fun addFirstViewBreadcrumbForSession(startTime: Long)
}

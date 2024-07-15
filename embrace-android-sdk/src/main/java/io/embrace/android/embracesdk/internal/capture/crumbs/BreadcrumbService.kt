package io.embrace.android.embracesdk.internal.capture.crumbs

import io.embrace.android.embracesdk.payload.PushNotificationBreadcrumb
import io.embrace.android.embracesdk.payload.TapBreadcrumb

/**
 * Service which stores breadcrumbs for the application.
 */
internal interface BreadcrumbService {

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
        type: TapBreadcrumb.TapBreadcrumbType
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
        type: PushNotificationBreadcrumb.NotificationType
    )
}

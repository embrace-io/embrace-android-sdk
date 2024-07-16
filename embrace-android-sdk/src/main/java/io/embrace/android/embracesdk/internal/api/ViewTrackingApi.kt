package io.embrace.android.embracesdk.internal.api

import android.app.Application
import io.embrace.android.embracesdk.internal.payload.TapBreadcrumb

internal interface ViewTrackingApi {

    /**
     * Register ComposeActivityListener as Activity Lifecycle Callbacks into the Application
     *
     * @param app Global application class
     */
    fun registerComposeActivityListener(app: Application)

    /**
     * Register ComposeActivityListener as Activity Lifecycle Callbacks into the Application
     *
     * @param app Global application class
     */
    fun unregisterComposeActivityListener(app: Application)

    /**
     * Log the start of a view.
     *
     * A matching call to endView must be made.
     *
     * @param name the name of the view to log
     */
    fun startView(name: String): Boolean

    /**
     * Log the end of a view.
     *
     * A matching call to startView must be made before this is called.
     *
     * @param name the name of the view to log
     */
    fun endView(name: String): Boolean

    /**
     * Logs a tap on a screen element.
     *
     * @param point       the coordinates of the screen tap
     * @param elementName the name of the element which was tapped
     * @param type        the type of tap that occurred
     */
    fun logTap(point: Pair<Float?, Float?>, elementName: String, type: TapBreadcrumb.TapBreadcrumbType)

    /**
     * Logs a React Native Redux Action.
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
     * Logs the fact that a particular view was entered.
     *
     * If the previously logged view has the same name, a duplicate view breadcrumb will not be
     * logged.
     *
     * @param screen the name of the view to log
     */
    fun logRnView(screen: String)
}

package io.embrace.android.embracesdk.internal.api

internal interface ViewTrackingApi {

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
     * Logs a React Native Redux Action.
     */
    fun logRnAction(
        name: String,
        startTime: Long,
        endTime: Long,
        properties: Map<String?, Any?>,
        bytesSent: Int,
        output: String,
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

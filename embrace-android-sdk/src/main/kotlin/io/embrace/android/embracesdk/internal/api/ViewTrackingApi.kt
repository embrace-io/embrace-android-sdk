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
}

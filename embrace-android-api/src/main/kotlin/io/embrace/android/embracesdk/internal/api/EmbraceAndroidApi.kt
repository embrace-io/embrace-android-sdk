package io.embrace.android.embracesdk.internal.api

import io.embrace.android.embracesdk.annotation.InternalApi

/**
 * Declares the functions that consist of Embrace's public API - specifically
 * those that are only declared on Android. You should not use the interface
 * [EmbraceAndroidApi] directly or implement it in your own custom classes,
 * as new functions may be added in the future. Use the Embrace class instead.
 */
@InternalApi
public interface EmbraceAndroidApi {

    /**
     * Records that a view 'started'. You should call this when your app starts displaying an
     * activity, a fragment, a screen, or any custom UI element, and you want to capture a
     * breadcrumb that this happens.
     *
     * A matching call to [.endView] must be made when the view is no longer
     * displayed.
     *
     * A maximum of 100 breadcrumbs will be recorded per session part, with a maximum length of 256
     * characters per view name.
     *
     * @param name the name of the view to log
     */
    public fun startView(name: String): Boolean

    /**
     * Records that a view 'ended'. You should call this when your app stops displaying an
     * activity, a fragment, a screen, or any custom UI element, and you want to capture a
     * breadcrumb that this happens.
     *
     * A matching call to [.startView] must be made when the view is first
     * displayed, or no breadcrumb will be logged.
     *
     * A maximum of 100 breadcrumbs will be recorded per session part, with a maximum length of 256
     * characters per view name.
     *
     * @param name the name of the view to log
     */
    public fun endView(name: String): Boolean

    /**
     * Notify the Embrace SDK that the application is starting to initialize. This is typically called at the beginning of the
     * Application.onCreate() function, before the Embrace SDK is started. Calling this will allow the SDK to more accurately gauge
     * when the application began to start.
     */
    public fun applicationInitStart()

    /**
     * Notify the Embrace SDK that the application has been fully created. This is typically called at the end of the
     * Application.onCreate() function. Calling this will allow the SDK to more accurately gather the details of the
     * trace recorded for app startup.
     */
    public fun applicationInitEnd()
}

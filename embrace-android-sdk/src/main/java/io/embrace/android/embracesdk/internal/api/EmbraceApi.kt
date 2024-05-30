package io.embrace.android.embracesdk.internal.api

import android.webkit.ConsoleMessage
import io.embrace.android.embracesdk.Embrace.LastRunEndState

/**
 * Declares the functions that consist of Embrace's public API. You should not use
 * [EmbraceApi] directly or implement it in your own custom classes,
 * as new functions may be added in future. Use the [Embrace] class instead.
 */
internal interface EmbraceApi {

    /**
     * Sets a custom app ID that overrides the one specified at build time. Must be called before
     * the SDK is started.
     *
     * @param appId custom app ID
     * @return true if the app ID could be set, false otherwise.
     */
    fun setAppId(appId: String): Boolean

    /**
     * Adds a breadcrumb.
     *
     * Breadcrumbs track a user's journey through the application and will be shown on the timeline.
     *
     * @param message the name of the breadcrumb to log
     */
    fun addBreadcrumb(message: String)

    /**
     * Retrieve the HTTP request header to extract trace ID from.
     *
     * @return the Trace ID header.
     */
    val traceIdHeader: String

    /**
     * Randomly generate a W3C-compliant traceparent
     */
    fun generateW3cTraceparent(): String

    /**
     * Get the user identifier assigned to the device by Embrace
     *
     * @return the device identifier created by Embrace
     */
    val deviceId: String

    /**
     * Listen to performance-tracking JavaScript previously embedded in the website's code.
     * The WebView being tracked must have JavaScript enabled.
     *
     * @param tag            a name used to identify the WebView being tracked
     * @param consoleMessage the console message collected from the WebView
     */
    fun trackWebViewPerformance(tag: String, consoleMessage: ConsoleMessage)

    /**
     * Listen to performance-tracking JavaScript previously embedded in the website's code.
     * The WebView being tracked must have JavaScript enabled.
     *
     * @param tag     a name used to identify the WebView being tracked
     * @param message the console message collected from the WebView
     */
    fun trackWebViewPerformance(tag: String, message: String)

    /**
     * Get the ID for the current session.
     * Returns null if a session has not been started yet or the SDK hasn't been initialized.
     *
     * @return The ID for the current Session, if available.
     */
    val currentSessionId: String?

    /**
     * Get the end state of the last run of the application.
     *
     * @return LastRunEndState enum value representing the end state of the last run.
     */
    val lastRunEndState: LastRunEndState
}

package io.embrace.android.embracesdk.arch.datasource

import io.embrace.android.embracesdk.internal.spans.CurrentSessionSpan
import io.embrace.android.embracesdk.internal.spans.EmbraceTracer

/**
 * Defines a 'data source'. This should be responsible for capturing a specific type
 * of data that will be sent to Embrace.
 *
 * @param T The type of the destination that the data will be sent to. This could be
 * either a [CurrentSessionSpan], [EmbraceTracer], or [LogWriter].
 *
 * See [EventDataSource], [SpanDataSource], and [LogDataSource] for more information.
 */
internal interface DataSource<T> {

    /**
     * The DataSource should call this function when it wants to capture a [EmbraceSpanEvent] or
     * [EmbraceSpanAttribute] and send it to the destination. This function is only intended
     * for one-time use - if you want to record a span, please use [startSpan] and [stopSpan].
     *
     * The [inputValidation] parameter should return true if the user inputs are valid.
     * (e.g. an empty string is not valid for a breadcrumb message).
     *
     * The [captureAction] parameter is a lambda that captures the data and sends it to the
     * destination. It will be called only if [inputValidation] returns true & no data capture
     * limits have been exceeded.
     *
     * This function returns true if data was successfully captured & false if not.
     * This is assumed to be the case if [captureAction] completed without throwing.
     */
    fun alterSessionSpan(inputValidation: () -> Boolean, captureAction: T.() -> Unit): Boolean

    /**
     * Enables data capture. This should include registering any listeners, and resetting
     * any state (if applicable).
     *
     * You should NOT attempt to track state within the [DataSource] with a boolean flag.
     */
    fun enableDataCapture()

    /**
     * Disables data capture. This should include unregistering any listeners, and resetting
     * any state (if applicable).
     *
     * You should NOT attempt to track state within the [DataSource] with a boolean flag.
     */
    fun disableDataCapture()

    /**
     * Resets any data capture limits since the last time [enableDataCapture] was called.
     */
    fun resetDataCaptureLimits()
}

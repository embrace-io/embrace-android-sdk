package io.embrace.android.embracesdk.arch

import io.embrace.android.embracesdk.internal.spans.CurrentSessionSpan
import io.embrace.android.embracesdk.internal.spans.EmbraceTracer

/**
 * Defines a 'data source'. This should be responsible for capturing a specific type
 * of data that will be sent to Embrace.
 *
 * @param T The type of the destination that the data will be sent to. This could be
 * either a [CurrentSessionSpan], [EmbraceTracer], or [LogService].
 *
 * See [EventDataSource], [SpanDataSource], and [LogDataSource] for more information.
 */
internal interface DataSource<T> {

    /**
     * The DataSource should call this function when it wants to capture data
     * and send it to the destination.
     */
    fun captureData(action: T.() -> Unit)

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
}

/**
 * A [DataSource] that adds either a [EmbraceSpanEvent] or [EmbraceSpanAttribute]
 * to the current session span.
 */
internal typealias EventDataSource = DataSource<SessionSpanWriter>

/**
 * A [DataSource] that adds or alters a new span on the [SpansService]
 */
internal typealias SpanDataSource = DataSource<EmbraceTracer>

/**
 * A [DataSource] that adds a new log to the log service.
 */
internal typealias LogDataSource = DataSource<LogService>

/**
 * Placeholder type for the log service.
 */
internal interface LogService

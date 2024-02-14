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
     * Register any listeners that are required for capturing data.
     */
    fun registerListeners()

    /**
     * Unregister any listeners that might be capturing data.
     */
    fun unregisterListeners()
}

/**
 * A [DataSource] that adds either a [EmbraceSpanEvent] or [EmbraceSpanAttribute]
 * to the current session span.
 */
internal interface EventDataSource : DataSource<CurrentSessionSpan>

/**
 * A [DataSource] that adds or alters a new span on the [SpansService]
 */
internal interface SpanDataSource : DataSource<EmbraceTracer>

/**
 * A [DataSource] that adds a new log to the log service.
 */
internal interface LogDataSource : DataSource<LogService>

/**
 * Placeholder type for the log service.
 */
internal interface LogService

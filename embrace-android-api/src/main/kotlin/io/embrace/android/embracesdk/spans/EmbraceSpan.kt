package io.embrace.android.embracesdk.spans

import io.opentelemetry.api.common.Attributes
import io.opentelemetry.api.trace.SpanContext

/**
 * Represents a Span that can be started and stopped with the appropriate [ErrorCode] if applicable. This wraps the OpenTelemetry Span
 * by adding an additional layer for local validation
 */
public interface EmbraceSpan {

    /**
     * The [SpanContext] for this [EmbraceSpan] instance. This is null if the span has not been started.
     */
    public val spanContext: SpanContext?

    /**
     * ID of the Trace that this Span belongs to. The format adheres to the OpenTelemetry standard for Trace IDs
     */
    public val traceId: String?

    /**
     * ID of the Span. The format adheres to the OpenTelemetry standard for Span IDs
     */
    public val spanId: String?

    /**
     * Returns true if and only if this Span has been started and has not been stopped
     */
    public val isRecording: Boolean

    /**
     * The Span that is the parent of this Span. If this is null, it means this Span is the root of the Trace.
     */
    public val parent: EmbraceSpan?

    /**
     * The auto termination mode for this span
     */
    public val autoTerminationMode: AutoTerminationMode

    /**
     * Start recording of the Span. Returns true if this call triggered the start of the recording. Returns false if the Span has already
     * been started or has been stopped.
     */
    public fun start(): Boolean = start(startTimeMs = null)

    /**
     * Start recording of the Span with the given start time. Returns true if this call triggered the start of the recording.
     * Returns false if the Span has already been started or has been stopped.
     */
    public fun start(startTimeMs: Long?): Boolean

    /**
     * Stop the recording of the Span to mark a successful completion of the underlying operation. Returns true if this call triggered
     * the stopping of the recording. Returns false if the Span has not been started or if has already been stopped.
     */
    public fun stop(): Boolean = stop(errorCode = null, endTimeMs = null)

    /**
     * Stop the recording of the Span with an [ErrorCode], a non-null value indicating an unsuccessful completion of the underlying
     * operation with the given reason. Returns true if this call triggered the stopping of the recording. Returns false if the Span has
     * not been started or if has already been stopped.
     */
    public fun stop(errorCode: ErrorCode?): Boolean = stop(errorCode = errorCode, endTimeMs = null)

    /**
     * Stop the recording of the Span at the given time to mark the successful completion of the underlying operation. Returns true
     * if this call triggered the stopping of the recording. Returns false if the Span has not been started or if has already been stopped.
     */
    public fun stop(endTimeMs: Long?): Boolean = stop(errorCode = null, endTimeMs = endTimeMs)

    /**
     * Stop the recording of the Span with an [ErrorCode] the the specific time, a non-null value indicating an unsuccessful completion of
     * the underlying operation with the given reason. Returns true if this call triggered the stopping of the recording. Returns false if
     * the Span has not been started or if has already been stopped.
     */
    public fun stop(errorCode: ErrorCode?, endTimeMs: Long?): Boolean

    /**
     * Add an [EmbraceSpanEvent] with the given [name] at the current time. Returns false if the Event was definitely not successfully
     * added. Returns true if the validation at the Embrace level has passed and the call to add the Event at the OpenTelemetry level was
     * successful.
     */
    public fun addEvent(
        name: String,
    ): Boolean = addEvent(name = name, timestampMs = null, attributes = null)

    /**
     * Add an [EmbraceSpanEvent] with the given [name] and [timestampMs]. Optionally, a set of attributes associated with the event can
     * be passed in. Returns false if the Event was definitely not successfully added. Returns true if the validation at the Embrace
     * level has passed and the call to add the Event at the OpenTelemetry level was successful.
     */
    public fun addEvent(
        name: String,
        timestampMs: Long?,
    ): Boolean = addEvent(name = name, timestampMs = timestampMs, attributes = null)

    /**
     * Add an [EmbraceSpanEvent] with the given [name]. If [timestampMs] is null, the current time will be used. Optionally, the specific
     * time of the event and a set of attributes can be passed in associated with the event. Returns false if the Event was definitely not
     * successfully added. Returns true if the validation at the Embrace level has passed and the call to add the Event at the
     * OpenTelemetry level was successful.
     */
    public fun addEvent(
        name: String,
        timestampMs: Long?,
        attributes: Map<String, String>?,
    ): Boolean

    /**
     * Record the given [Throwable] as a Span Event at the current time. Returns false if event was definitely not recorded. Returns true
     * if the validation at the Embrace level has passed and the call to add the Event at the OpenTelemetry level was successful.
     */
    public fun recordException(exception: Throwable): Boolean = recordException(exception, null)

    /**
     * Record the given [Throwable] as a Span Event at the current with the given set of [Attributes]. Returns false if event was
     * definitely not recorded. Returns true if the validation at the Embrace level has passed and the call to add the Event at the
     * OpenTelemetry level was successful.
     */
    public fun recordException(exception: Throwable, attributes: Map<String, String>?): Boolean

    /**
     * Add the given key-value pair as an Attribute to the Event. Returns false if the Attribute was definitely not added. Returns true
     * if the validation at the Embrace Level has passed and the call to add the Attribute at the OpenTelemetry level was successful.
     */
    public fun addAttribute(key: String, value: String): Boolean

    /**
     * Update the name of the span. Returns false if the update was not successful, like when it has already been stopped
     */
    public fun updateName(newName: String): Boolean
}

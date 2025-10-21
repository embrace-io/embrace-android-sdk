package io.embrace.android.embracesdk.spans

import io.embrace.opentelemetry.kotlin.ExperimentalApi
import io.embrace.opentelemetry.kotlin.tracing.model.SpanContext

/**
 * Represents a Span that can be started and stopped with the appropriate [ErrorCode] if applicable. This wraps the OpenTelemetry Span
 * by adding an additional layer for local validation
 */
@OptIn(ExperimentalApi::class)
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
     * Start recording of the Span with the given start time. Returns true if this call triggered the start of the recording.
     * Returns false if the Span has already been started or has been stopped.
     */
    public fun start(startTimeMs: Long? = null): Boolean

    /**
     * Stop the recording of the Span with an [ErrorCode] the the specific time, a non-null value indicating an unsuccessful completion of
     * the underlying operation with the given reason. Returns true if this call triggered the stopping of the recording. Returns false if
     * the Span has not been started or if has already been stopped.
     */
    public fun stop(
        errorCode: ErrorCode? = null,
        endTimeMs: Long? = null,
    ): Boolean

    /**
     * Add an [EmbraceSpanEvent] with the given [name]. If [timestampMs] is null, the current time will be used. Optionally, the specific
     * time of the event and a set of attributes can be passed in associated with the event. Returns false if the Event was definitely not
     * successfully added. Returns true if the validation at the Embrace level has passed and the call to add the Event at the
     * OpenTelemetry level was successful.
     */
    public fun addEvent(
        name: String,
        timestampMs: Long? = null,
        attributes: Map<String, String> = emptyMap(),
    ): Boolean

    /**
     * Record the given [Throwable] as a Span Event at the current with the given set of attributes. Returns false if event was
     * definitely not recorded. Returns true if the validation at the Embrace level has passed and the call to add the Event at the
     * OpenTelemetry level was successful.
     */
    public fun recordException(
        exception: Throwable,
        attributes: Map<String, String> = emptyMap(),
    ): Boolean

    /**
     * Add the given key-value pair as an Attribute to the Event. Returns false if the Attribute was definitely not added. Returns true
     * if the validation at the Embrace Level has passed and the call to add the Attribute at the OpenTelemetry level was successful.
     */
    public fun addAttribute(key: String, value: String): Boolean

    /**
     * Update the name of the span. Returns false if the update was not successful, like when it has already been stopped
     */
    public fun updateName(newName: String): Boolean

    /**
     * Add a link to the given [EmbraceSpan] with the given attributes
     */
    public fun addLink(
        linkedSpan: EmbraceSpan,
        attributes: Map<String, String> = emptyMap(),
    ): Boolean {
        val spanContext = linkedSpan.spanContext
        return if (spanContext != null) {
            addLink(linkedSpanContext = spanContext, attributes = attributes)
        } else {
            false
        }
    }

    /**
     * Add a link to the span with the given [SpanContext] with the given attributes
     */
    public fun addLink(
        linkedSpanContext: SpanContext,
        attributes: Map<String, String> = emptyMap(),
    ): Boolean
}

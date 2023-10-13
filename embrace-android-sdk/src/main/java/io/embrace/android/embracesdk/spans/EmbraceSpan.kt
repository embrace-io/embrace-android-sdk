package io.embrace.android.embracesdk.spans

import io.embrace.android.embracesdk.BetaApi

/**
 * Represents a Span that can be started and stopped with the appropriate [ErrorCode] if applicable. This wraps the OpenTelemetry Span
 * by adding an additional layer for local validation
 */
@BetaApi
public interface EmbraceSpan {
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
     * Start recording of the Span. Returns true if this call triggered the start of the recording. Returns false if the Span has already
     * been started or has been stopped.
     */
    public fun start(): Boolean

    /**
     * Stop the recording of the Span with no [ErrorCode], indicating a successful completion of the underlying operation. Returns true
     * if this call triggered the stopping of the recording. Returns false if the Span has not been started or if has already been stopped.
     */
    public fun stop(): Boolean

    /**
     * Stop the recording of the Span with an [ErrorCode], a non-null value indicating an unsuccessful completion of the underlying
     * operation with the given reason. Returns true if this call triggered the stopping of the recording. Returns false if the Span has
     * not been started or if has already been stopped.
     */
    public fun stop(errorCode: ErrorCode?): Boolean

    /**
     * Add an [EmbraceSpanEvent] with the given [name] at the current time. Returns false if the Event was definitely not successfully
     * added. Returns true if the validation at the Embrace level has passed and the call to add the Event at the OpenTelemetry level was
     * successful.
     */
    public fun addEvent(
        name: String
    ): Boolean

    /**
     * Add an [EmbraceSpanEvent] with the given [name]. If [time] is null, the current time will be used. Optionally, the specific
     * time of the event and a set of attributes can be passed in associated with the event. Returns false if the Event was definitely not
     * successfully added. Returns true if the validation at the Embrace level has passed and the call to add the Event at the
     * OpenTelemetry level was successful.
     */
    public fun addEvent(
        name: String,
        time: Long?,
        attributes: Map<String, String>?
    ): Boolean

    /**
     * Add the given key-value pair as an Attribute to the Event. Returns false if the Attribute was definitely not added. Returns true
     * if the validation at the Embrace Level has passed and the call to add the Attribute at the OpenTelemetry level was successful.
     */
    public fun addAttribute(key: String, value: String): Boolean
}

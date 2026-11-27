package io.embrace.android.embracesdk.spans

import io.opentelemetry.kotlin.tracing.SpanContext

/**
 * Configures a span before it starts. Attributes and links added here are visible to samplers.
 */
public interface SpanStartAction {

    /**
     * Add the given key-value pair as an attribute.
     */
    public fun addAttribute(key: String, value: String)

    /**
     * Add an event with the given [name]. If [timestampMs] is null, the current time will be used.
     */
    public fun addEvent(
        name: String,
        timestampMs: Long? = null,
        attributes: Map<String, String> = emptyMap(),
    )

    /**
     * Add a link to the given [EmbraceSpan] with the given attributes.
     */
    public fun addLink(
        linkedSpan: EmbraceSpan,
        attributes: Map<String, String> = emptyMap(),
    )

    /**
     * Add a link to the span with the given [SpanContext] with the given attributes.
     */
    public fun addLink(
        linkedSpanContext: SpanContext,
        attributes: Map<String, String> = emptyMap(),
    )
}

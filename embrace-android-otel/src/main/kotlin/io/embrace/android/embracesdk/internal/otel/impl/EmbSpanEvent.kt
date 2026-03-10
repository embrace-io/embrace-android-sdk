package io.embrace.android.embracesdk.internal.otel.impl

import io.opentelemetry.kotlin.attributes.AttributesMutator
import io.opentelemetry.kotlin.tracing.model.SpanEvent

internal class EmbSpanEvent(
    override val name: String,
    override val timestamp: Long,
    private val attrs: EmbAttributesMutator,
) : SpanEvent, AttributesMutator by attrs {
    override val attributes: Map<String, Any> get() = attrs.attributes
}

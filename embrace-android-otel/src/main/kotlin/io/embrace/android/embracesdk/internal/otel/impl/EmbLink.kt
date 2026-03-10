package io.embrace.android.embracesdk.internal.otel.impl

import io.opentelemetry.kotlin.attributes.AttributesMutator
import io.opentelemetry.kotlin.tracing.model.SpanContext
import io.opentelemetry.kotlin.tracing.model.SpanLink

internal class EmbLink(
    override val spanContext: SpanContext,
    private val attrs: EmbAttributesMutator,
) : SpanLink, AttributesMutator by attrs {
    override val attributes: Map<String, Any> get() = attrs.attributes
}

package io.embrace.android.embracesdk.internal.otel.spans

import io.opentelemetry.api.trace.SpanContext

data class EmbraceLinkData(
    val spanContext: SpanContext,
    val attributes: Map<String, String>
)

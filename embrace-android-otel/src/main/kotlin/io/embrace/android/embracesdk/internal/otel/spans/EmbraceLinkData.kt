package io.embrace.android.embracesdk.internal.otel.spans

import io.embrace.opentelemetry.kotlin.tracing.model.SpanContext

data class EmbraceLinkData(
    val spanContext: SpanContext,
    val attributes: Map<String, String>,
)

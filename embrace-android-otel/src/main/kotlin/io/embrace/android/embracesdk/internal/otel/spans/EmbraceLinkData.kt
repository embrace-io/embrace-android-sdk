package io.embrace.android.embracesdk.internal.otel.spans

import io.embrace.opentelemetry.kotlin.ExperimentalApi
import io.embrace.opentelemetry.kotlin.tracing.model.SpanContext

@OptIn(ExperimentalApi::class)
data class EmbraceLinkData(
    val spanContext: SpanContext,
    val attributes: Map<String, String>,
)

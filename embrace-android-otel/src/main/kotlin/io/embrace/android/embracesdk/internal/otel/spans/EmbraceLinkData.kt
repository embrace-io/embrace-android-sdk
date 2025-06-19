package io.embrace.android.embracesdk.internal.otel.spans

import io.embrace.opentelemetry.kotlin.aliases.OtelJavaSpanContext

data class EmbraceLinkData(
    val spanContext: OtelJavaSpanContext,
    val attributes: Map<String, String>
)

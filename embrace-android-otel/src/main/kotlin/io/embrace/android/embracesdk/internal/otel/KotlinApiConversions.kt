package io.embrace.android.embracesdk.internal.otel

import io.embrace.android.embracesdk.internal.payload.Span.Status
import io.embrace.opentelemetry.kotlin.ExperimentalApi
import io.embrace.opentelemetry.kotlin.aliases.OtelJavaSpanContext
import io.embrace.opentelemetry.kotlin.k2j.tracing.toOtelKotlin
import io.embrace.opentelemetry.kotlin.tracing.StatusCode
import io.embrace.opentelemetry.kotlin.tracing.model.SpanContext

@OptIn(ExperimentalApi::class)
fun StatusCode.toEmbracePayload(): Status = when (this) {
    is StatusCode.Error -> Status.ERROR
    StatusCode.Ok -> Status.OK
    StatusCode.Unset -> Status.UNSET
}

@OptIn(ExperimentalApi::class)
fun OtelJavaSpanContext.toOtelKotlin(): SpanContext = this.toOtelKotlin()

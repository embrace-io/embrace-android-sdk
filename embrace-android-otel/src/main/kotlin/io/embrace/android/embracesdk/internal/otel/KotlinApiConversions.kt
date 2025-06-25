package io.embrace.android.embracesdk.internal.otel

import io.embrace.android.embracesdk.internal.payload.Span.Status
import io.embrace.opentelemetry.kotlin.StatusCode
import io.embrace.opentelemetry.kotlin.aliases.OtelJavaSpanKind
import io.embrace.opentelemetry.kotlin.aliases.OtelJavaStatusCode
import io.embrace.opentelemetry.kotlin.tracing.SpanKind

internal fun StatusCode.toOtelJava(): OtelJavaStatusCode = when (this) {
    is StatusCode.Unset -> OtelJavaStatusCode.UNSET
    is StatusCode.Ok -> OtelJavaStatusCode.OK
    is StatusCode.Error -> OtelJavaStatusCode.ERROR
}

internal fun OtelJavaStatusCode.toOtelKotlin(): StatusCode = when (this) {
    OtelJavaStatusCode.UNSET -> StatusCode.Unset
    OtelJavaStatusCode.OK -> StatusCode.Ok
    OtelJavaStatusCode.ERROR -> StatusCode.Error(null)
}

fun OtelJavaSpanKind.toOtelKotlin(): SpanKind = when (this) {
    OtelJavaSpanKind.SERVER -> SpanKind.SERVER
    OtelJavaSpanKind.CLIENT -> SpanKind.CLIENT
    OtelJavaSpanKind.PRODUCER -> SpanKind.PRODUCER
    OtelJavaSpanKind.CONSUMER -> SpanKind.CONSUMER
    OtelJavaSpanKind.INTERNAL -> SpanKind.INTERNAL
}

fun StatusCode.toEmbracePayload(): Status = when (this) {
    is StatusCode.Error -> Status.ERROR
    StatusCode.Ok -> Status.OK
    StatusCode.Unset -> Status.UNSET
}

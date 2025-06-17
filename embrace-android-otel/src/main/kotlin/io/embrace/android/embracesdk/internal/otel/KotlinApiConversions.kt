package io.embrace.android.embracesdk.internal.otel

import io.embrace.android.embracesdk.internal.otel.wrapper.OtelJavaSpanKind
import io.embrace.android.embracesdk.internal.otel.wrapper.OtelJavaStatusCode
import io.embrace.android.embracesdk.internal.payload.Span
import io.embrace.opentelemetry.kotlin.StatusCode
import io.embrace.opentelemetry.kotlin.tracing.SpanKind
import io.opentelemetry.api.common.AttributeKey

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

fun StatusCode.toEmbracePayload(): Span.Status = when (this) {
    is StatusCode.Error -> Span.Status.ERROR
    StatusCode.Ok -> Span.Status.OK
    StatusCode.Unset -> Span.Status.UNSET
}

internal fun String.toOtelJavaAttributeKey() = AttributeKey.stringKey(this)

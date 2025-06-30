package io.embrace.android.embracesdk.internal.otel.wrapper

import io.embrace.opentelemetry.kotlin.ExperimentalApi
import io.embrace.opentelemetry.kotlin.tracing.model.ReadableSpan
import io.opentelemetry.sdk.trace.data.SpanData

@OptIn(ExperimentalApi::class)
internal fun SpanData.toReadableSpan(): ReadableSpan {
    TODO("not implemented yet")
}

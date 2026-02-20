package io.embrace.android.embracesdk.internal.otel

import io.embrace.android.embracesdk.internal.payload.Span.Status
import io.opentelemetry.kotlin.ExperimentalApi
import io.opentelemetry.kotlin.tracing.StatusCode
import io.opentelemetry.kotlin.tracing.data.StatusData

@OptIn(ExperimentalApi::class)
fun StatusCode.toEmbracePayload(): Status = when (this) {
    StatusCode.ERROR -> Status.ERROR
    StatusCode.OK -> Status.OK
    StatusCode.UNSET -> Status.UNSET
}

@OptIn(ExperimentalApi::class)
fun StatusData.toEmbracePayload(): Status = when (this) {
    is StatusData.Error -> Status.ERROR
    StatusData.Ok -> Status.OK
    StatusData.Unset -> Status.UNSET
}

package io.embrace.android.embracesdk.internal.otel.spans

import io.embrace.android.embracesdk.internal.arch.datasource.SpanEvent
import io.embrace.android.embracesdk.internal.arch.datasource.SpanEventImpl
import io.embrace.android.embracesdk.internal.arch.schema.ErrorCodeAttribute
import io.embrace.android.embracesdk.spans.EmbraceSpanEvent
import io.embrace.android.embracesdk.spans.ErrorCode

/**
 * Converters that translate the public API types ([ErrorCode], [EmbraceSpanEvent]) into the SDK's internal
 * representations ([ErrorCodeAttribute], [SpanEvent]). These are the single conversions performed at the public API
 * boundary; internally the SDK uses the internal representations end-to-end, avoiding redundant round-trips.
 */

fun ErrorCode.toErrorCodeAttribute(): ErrorCodeAttribute = when (this) {
    ErrorCode.FAILURE -> ErrorCodeAttribute.Failure
    ErrorCode.USER_ABANDON -> ErrorCodeAttribute.UserAbandon
    ErrorCode.UNKNOWN -> ErrorCodeAttribute.Unknown
}

fun EmbraceSpanEvent.toSpanEvent(): SpanEvent = SpanEventImpl(name, timestampNanos, attributes)

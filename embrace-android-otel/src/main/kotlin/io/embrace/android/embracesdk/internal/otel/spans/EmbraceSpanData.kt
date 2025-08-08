package io.embrace.android.embracesdk.internal.otel.spans

import io.embrace.android.embracesdk.internal.payload.Link
import io.embrace.android.embracesdk.spans.EmbraceSpanEvent
import io.embrace.opentelemetry.kotlin.ExperimentalApi
import io.embrace.opentelemetry.kotlin.tracing.StatusCode

/**
 * Serializable representation of [EmbraceSpanData]
 */
@OptIn(ExperimentalApi::class)
data class EmbraceSpanData(
    val traceId: String,

    val spanId: String,

    val parentSpanId: String?,

    val name: String,

    val startTimeNanos: Long,

    val endTimeNanos: Long,

    val status: StatusCode = StatusCode.UNSET,

    val events: List<EmbraceSpanEvent> = emptyList(),

    val attributes: Map<String, String> = emptyMap(),

    val links: List<Link> = emptyList(),
)

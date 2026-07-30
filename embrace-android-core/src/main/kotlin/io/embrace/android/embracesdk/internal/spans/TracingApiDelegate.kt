package io.embrace.android.embracesdk.internal.spans

import io.embrace.android.embracesdk.internal.otel.spans.SpanService
import io.embrace.android.embracesdk.internal.otel.spans.toErrorCodeAttribute
import io.embrace.android.embracesdk.internal.otel.spans.toSpanEvent
import io.embrace.android.embracesdk.spans.AutoTerminationMode
import io.embrace.android.embracesdk.spans.EmbraceSpan
import io.embrace.android.embracesdk.spans.EmbraceSpanEvent
import io.embrace.android.embracesdk.spans.ErrorCode
import io.embrace.android.embracesdk.spans.TracingApi

class TracingApiDelegate(
    private val spanService: SpanService,
) : TracingApi {

    override fun createSpan(
        name: String,
        parent: EmbraceSpan?,
        autoTerminationMode: AutoTerminationMode,
    ): EmbraceSpan =
        spanService.createSpan(
            name = name,
            parent = parent,
            internal = false,
            autoTerminationMode = autoTerminationMode,
        )

    override fun startSpan(
        name: String,
        parent: EmbraceSpan?,
        startTimeMs: Long?,
        autoTerminationMode: AutoTerminationMode,
    ): EmbraceSpan =
        spanService.startSpan(
            name = name,
            parent = parent,
            startTimeMs = startTimeMs,
            internal = false,
            autoTerminationMode = autoTerminationMode,
        )

    override fun <T> recordSpan(
        name: String,
        parent: EmbraceSpan?,
        attributes: Map<String, String>,
        events: List<EmbraceSpanEvent>,
        autoTerminationMode: AutoTerminationMode,
        code: () -> T,
    ): T = spanService.recordSpan(
        name = name,
        parent = parent,
        internal = false,
        attributes = attributes,
        events = events.map { it.toSpanEvent() },
        autoTerminationMode = autoTerminationMode,
        code = code,
    )

    override fun recordCompletedSpan(
        name: String,
        startTimeMs: Long,
        endTimeMs: Long,
        errorCode: ErrorCode?,
        parent: EmbraceSpan?,
        attributes: Map<String, String>,
        events: List<EmbraceSpanEvent>,
    ): Boolean = spanService.recordCompletedSpan(
        name = name,
        startTimeMs = startTimeMs,
        endTimeMs = endTimeMs,
        parent = parent,
        internal = false,
        attributes = attributes,
        events = events.map { it.toSpanEvent() },
        errorCode = errorCode?.toErrorCodeAttribute(),
    )

    override fun getSpan(spanId: String): EmbraceSpan? = spanService.getSpan(spanId = spanId)
}

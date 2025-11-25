package io.embrace.android.embracesdk.internal.instrumentation.startup.activity

import io.embrace.android.embracesdk.internal.arch.datasource.SpanEvent
import io.embrace.android.embracesdk.internal.arch.schema.ErrorCodeAttribute

/**
 * Interface containing methods that will add more data to any on-going UI Load trace
 */
interface UiLoadTraceModifier {

    /**
     * Add attribute to the root span of the in-process UI Load trace being recorded for the given instanceId.
     */
    fun addAttribute(instanceId: Int, key: String, value: String)

    /**
     * Add a child span with the given parameters to the in-process UI Load trace being recorded for the given instanceId
     */
    fun addChildSpan(
        instanceId: Int,
        name: String,
        startTimeMs: Long,
        endTimeMs: Long,
        attributes: Map<String, String> = emptyMap(),
        events: List<SpanEvent> = emptyList(),
        errorCode: ErrorCodeAttribute? = null,
    )
}

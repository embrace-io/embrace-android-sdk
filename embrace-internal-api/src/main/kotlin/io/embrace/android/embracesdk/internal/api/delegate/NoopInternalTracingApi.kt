package io.embrace.android.embracesdk.internal.api.delegate

import io.embrace.android.embracesdk.internal.InternalTracingApi
import io.embrace.android.embracesdk.spans.ErrorCode

internal class NoopInternalTracingApi : InternalTracingApi {
    override fun startSpan(name: String, parentSpanId: String?, startTimeMs: Long?): String? {
        return null
    }

    override fun stopSpan(spanId: String, errorCode: ErrorCode?, endTimeMs: Long?): Boolean {
        return false
    }

    override fun addSpanEvent(
        spanId: String,
        name: String,
        timestampMs: Long?,
        attributes: Map<String, String>?,
    ): Boolean {
        return false
    }

    override fun addSpanAttribute(spanId: String, key: String, value: String): Boolean {
        return false
    }

    override fun <T> recordSpan(
        name: String,
        parentSpanId: String?,
        attributes: Map<String, String>?,
        events: List<Map<String, Any>>?,
        code: () -> T,
    ): T {
        return code()
    }

    override fun recordCompletedSpan(
        name: String,
        startTimeMs: Long,
        endTimeMs: Long,
        errorCode: ErrorCode?,
        parentSpanId: String?,
        attributes: Map<String, String>?,
        events: List<Map<String, Any>>?,
    ): Boolean {
        return false
    }
}

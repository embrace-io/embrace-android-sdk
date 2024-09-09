package io.embrace.android.embracesdk.internal.spans

import io.embrace.android.embracesdk.internal.InternalTracingApi
import io.embrace.android.embracesdk.internal.clock.nanosToMillis
import io.embrace.android.embracesdk.internal.clock.normalizeTimestampAsMillis
import io.embrace.android.embracesdk.spans.EmbraceSpan
import io.embrace.android.embracesdk.spans.EmbraceSpanEvent
import io.embrace.android.embracesdk.spans.ErrorCode

class InternalTracer(
    private val spanRepository: SpanRepository,
    private val embraceTracer: EmbraceTracer,
) : InternalTracingApi {

    override fun startSpan(name: String, parentSpanId: String?, startTimeMs: Long?): String? {
        val parent = validateParent(parentSpanId)
        return if (parent.isValid) {
            embraceTracer.startSpan(
                name = name,
                parent = parent.spanReference,
                startTimeMs = startTimeMs
            )?.spanId
        } else {
            null
        }
    }

    override fun stopSpan(spanId: String, errorCode: ErrorCode?, endTimeMs: Long?): Boolean =
        spanRepository.getSpan(spanId = spanId)?.stop(
            errorCode = errorCode,
            endTimeMs = endTimeMs?.normalizeTimestampAsMillis()
        ) ?: false

    override fun <T> recordSpan(
        name: String,
        parentSpanId: String?,
        attributes: Map<String, String>?,
        events: List<Map<String, Any>>?,
        code: () -> T
    ): T {
        val parent = validateParent(parentSpanId)
        return if (parent.isValid) {
            embraceTracer.recordSpan(
                name = name,
                parent = parent.spanReference,
                attributes = attributes,
                events = events?.mapNotNull { mapToEvent(it) },
                code = code
            )
        } else {
            code()
        }
    }

    override fun recordCompletedSpan(
        name: String,
        startTimeMs: Long,
        endTimeMs: Long,
        errorCode: ErrorCode?,
        parentSpanId: String?,
        attributes: Map<String, String>?,
        events: List<Map<String, Any>>?
    ): Boolean {
        val parent = validateParent(parentSpanId)
        return if (parent.isValid) {
            embraceTracer.recordCompletedSpan(
                name = name,
                startTimeMs = startTimeMs,
                endTimeMs = endTimeMs,
                errorCode = errorCode,
                parent = parent.spanReference,
                attributes = attributes,
                events = events?.mapNotNull { mapToEvent(it) }
            )
        } else {
            false
        }
    }

    override fun addSpanEvent(spanId: String, name: String, timestampMs: Long?, attributes: Map<String, String>?): Boolean =
        spanRepository.getSpan(spanId = spanId)?.addEvent(
            name = name,
            timestampMs = timestampMs?.normalizeTimestampAsMillis(),
            attributes = attributes
        ) ?: false

    override fun addSpanAttribute(spanId: String, key: String, value: String): Boolean =
        spanRepository.getSpan(spanId = spanId)?.addAttribute(
            key = key,
            value = value
        ) ?: false

    private fun mapToEvent(map: Map<String, Any>): EmbraceSpanEvent? {
        val name = map["name"]
        val timestampMs = map["timestampMs"] as? Long?
        val timestampNanos = (map["timestampNanos"] as? Long?)?.nanosToMillis()
        val attributes = map["attributes"]

        // If timestampMs is specified but isn't the right type, return and don't create the event
        if (timestampMs == null && map["timestampMs"] != null) {
            return null
        }

        // If timestampMs is valid, use it
        // else if timestampNanos is valid, use it
        // else if timestampNanos isn't specified, use the current time in millis
        // Otherwise, it means we have an invalid type of timestampNanos so we don't create the event
        val validatedTimeMs = timestampMs ?: timestampNanos ?: if (map["timestampNanos"] == null) {
            embraceTracer.getSdkCurrentTimeMs()
        } else {
            return null
        }

        return if (name is String && attributes is Map<*, *>?) {
            EmbraceSpanEvent.create(
                name = name,
                timestampMs = validatedTimeMs,
                attributes = attributes?.let { toStringMap(it) }
            )
        } else {
            null
        }
    }

    private fun validateParent(parentSpanId: String?): Parent {
        val parentSpan = parentSpanId?.let { spanRepository.getSpan(spanId = parentSpanId) }
        return Parent(isValid = parentSpanId == null || parentSpan != null, spanReference = parentSpan)
    }

    private fun toStringMap(map: Map<*, *>): Map<String, String> =
        map.entries
            .filter { it.key is String && it.value is String }
            .associate { Pair(it.key.toString(), it.value.toString()) }

    private data class Parent(val isValid: Boolean, val spanReference: EmbraceSpan?)
}

package io.embrace.android.embracesdk.internal.spans

import io.embrace.android.embracesdk.internal.InternalTracingApi
import io.embrace.android.embracesdk.internal.clock.Clock
import io.embrace.android.embracesdk.spans.EmbraceSpan
import io.embrace.android.embracesdk.spans.EmbraceSpanEvent
import io.embrace.android.embracesdk.spans.ErrorCode

internal class InternalTracer(
    private val clock: Clock,
    private val spansRepository: SpansRepository,
    private val embraceTracer: EmbraceTracer,
) : InternalTracingApi {

    override fun startSpan(name: String, parentSpanId: String?): String? {
        val parent = validateParent(parentSpanId)
        return if (parent.isValid) {
            embraceTracer.createSpan(
                name = name,
                parent = parent.spanReference
            )?.run {
                start()
                spanId
            }
        } else {
            null
        }
    }

    override fun stopSpan(spanId: String, errorCode: ErrorCode?): Boolean =
        spansRepository.getSpan(spanId = spanId)?.stop(errorCode = errorCode) ?: false

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
                code = code
            )
        } else {
            code()
        }
    }

    override fun recordCompletedSpan(
        name: String,
        startTimeNanos: Long,
        endTimeNanos: Long,
        errorCode: ErrorCode?,
        parentSpanId: String?,
        attributes: Map<String, String>?,
        events: List<Map<String, Any>>?
    ): Boolean {
        val parent = validateParent(parentSpanId)
        return if (parent.isValid) {
            embraceTracer.recordCompletedSpan(
                name = name,
                startTimeNanos = startTimeNanos,
                endTimeNanos = endTimeNanos,
                errorCode = errorCode,
                parent = parent.spanReference,
                attributes = attributes,
                events = events?.mapNotNull { mapToEvent(it) }
            )
        } else {
            false
        }
    }

    override fun addSpanEvent(spanId: String, name: String, time: Long?, attributes: Map<String, String>?): Boolean =
        spansRepository.getSpan(spanId = spanId)?.addEvent(
            name = name,
            time = time,
            attributes = attributes
        ) ?: false

    override fun addSpanAttribute(spanId: String, key: String, value: String): Boolean =
        spansRepository.getSpan(spanId = spanId)?.addAttribute(
            key = key,
            value = value
        ) ?: false

    private fun mapToEvent(map: Map<String, Any>): EmbraceSpanEvent? {
        val name = map["name"]
        val timestampNanos = map["timestampNanos"]
        val attributes = map["attributes"]
        return if (name is String && timestampNanos is Long? && attributes is Map<*, *>?) {
            EmbraceSpanEvent.create(
                name = name,
                timestampNanos = timestampNanos ?: clock.nowInNanos(),
                attributes = attributes?.let { toStringMap(it) }
            )
        } else {
            null
        }
    }

    private fun validateParent(parentSpanId: String?): Parent {
        val parentSpan = parentSpanId?.let { spansRepository.getSpan(spanId = parentSpanId) }
        return Parent(isValid = parentSpanId == null || parentSpan != null, spanReference = parentSpan)
    }

    private fun toStringMap(map: Map<*, *>): Map<String, String> =
        map.entries
            .filter { it.key is String && it.value is String }
            .associate { Pair(it.key.toString(), it.value.toString()) }

    private data class Parent(val isValid: Boolean, val spanReference: EmbraceSpan?)
}

package io.embrace.android.embracesdk.fakes

import io.embrace.android.embracesdk.spans.EmbraceSpan
import io.embrace.android.embracesdk.spans.EmbraceSpanEvent
import io.embrace.android.embracesdk.spans.ErrorCode
import io.embrace.android.embracesdk.spans.TracingApi

internal class FakeTracingApi : TracingApi {

    val createdSpans = mutableListOf<String>()

    override fun startSpan(name: String): EmbraceSpan? {
        TODO("Not yet implemented")
    }

    override fun startSpan(name: String, parent: EmbraceSpan?): EmbraceSpan? {
        TODO("Not yet implemented")
    }

    override fun createSpan(name: String): EmbraceSpan? {
        createdSpans.add(name)
        return null
    }

    override fun createSpan(name: String, parent: EmbraceSpan?): EmbraceSpan? {
        TODO("Not yet implemented")
    }

    override fun <T> recordSpan(name: String, code: () -> T): T {
        TODO("Not yet implemented")
    }

    override fun <T> recordSpan(name: String, parent: EmbraceSpan?, code: () -> T): T {
        TODO("Not yet implemented")
    }

    override fun <T> recordSpan(
        name: String,
        attributes: Map<String, String>?,
        events: List<EmbraceSpanEvent>?,
        code: () -> T
    ): T {
        TODO("Not yet implemented")
    }

    override fun <T> recordSpan(
        name: String,
        parent: EmbraceSpan?,
        attributes: Map<String, String>?,
        events: List<EmbraceSpanEvent>?,
        code: () -> T
    ): T {
        TODO("Not yet implemented")
    }

    override fun recordCompletedSpan(
        name: String,
        startTimeNanos: Long,
        endTimeNanos: Long
    ): Boolean {
        TODO("Not yet implemented")
    }

    override fun recordCompletedSpan(
        name: String,
        startTimeNanos: Long,
        endTimeNanos: Long,
        errorCode: ErrorCode?
    ): Boolean {
        TODO("Not yet implemented")
    }

    override fun recordCompletedSpan(
        name: String,
        startTimeNanos: Long,
        endTimeNanos: Long,
        parent: EmbraceSpan?
    ): Boolean {
        TODO("Not yet implemented")
    }

    override fun recordCompletedSpan(
        name: String,
        startTimeNanos: Long,
        endTimeNanos: Long,
        errorCode: ErrorCode?,
        parent: EmbraceSpan?
    ): Boolean {
        TODO("Not yet implemented")
    }

    override fun recordCompletedSpan(
        name: String,
        startTimeNanos: Long,
        endTimeNanos: Long,
        attributes: Map<String, String>?,
        events: List<EmbraceSpanEvent>?
    ): Boolean {
        TODO("Not yet implemented")
    }

    override fun recordCompletedSpan(
        name: String,
        startTimeNanos: Long,
        endTimeNanos: Long,
        errorCode: ErrorCode?,
        parent: EmbraceSpan?,
        attributes: Map<String, String>?,
        events: List<EmbraceSpanEvent>?
    ): Boolean {
        TODO("Not yet implemented")
    }

    override fun getSpan(spanId: String): EmbraceSpan? {
        TODO("Not yet implemented")
    }

    @Deprecated("Not required. Use Embrace.isStarted() to know when the full tracing API is available")
    override fun isTracingAvailable(): Boolean {
        TODO("Not yet implemented")
    }
}

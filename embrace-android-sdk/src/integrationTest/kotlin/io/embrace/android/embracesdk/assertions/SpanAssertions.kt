package io.embrace.android.embracesdk.assertions

import io.embrace.android.embracesdk.internal.spans.EmbraceSpanData
import io.embrace.android.embracesdk.internal.spans.isKey
import io.embrace.android.embracesdk.internal.spans.isPrivate
import io.embrace.android.embracesdk.spans.EmbraceSpanEvent
import io.embrace.android.embracesdk.spans.ErrorCode
import io.opentelemetry.api.trace.StatusCode
import org.junit.Assert.assertEquals
import java.util.concurrent.TimeUnit

/**
 * Assert the [EmbraceSpanData] is as expected
 */
internal fun assertEmbraceSpanData(
    span: EmbraceSpanData?,
    expectedStartTimeMs: Long,
    expectedEndTimeMs: Long,
    expectedParentId: String,
    expectedTraceId: String? = null,
    expectedStatus: StatusCode = StatusCode.OK,
    errorCode: ErrorCode? = null,
    expectedCustomAttributes: Map<String, String> = emptyMap(),
    expectedEvents: List<EmbraceSpanEvent> = emptyList(),
    private: Boolean = false,
    key: Boolean = false,
) {
    checkNotNull(span)
    with(span) {
        assertEquals(TimeUnit.MILLISECONDS.toNanos(expectedStartTimeMs), startTimeNanos)
        assertEquals(TimeUnit.MILLISECONDS.toNanos(expectedEndTimeMs), endTimeNanos)
        assertEquals(expectedParentId, parentSpanId)
        if (expectedTraceId != null) {
            assertEquals(expectedTraceId, traceId)
        } else {
            assertEquals(32, traceId.length)
        }
        assertEquals(expectedStatus, status)
        assertEquals(errorCode?.name, attributes[errorCode?.keyName()])
        expectedCustomAttributes.forEach { entry ->
            assertEquals(entry.value, attributes[entry.key])
        }
        assertEquals(expectedEvents, events)
        assertEquals(private, isPrivate())
        assertEquals(key, isKey())
    }
}
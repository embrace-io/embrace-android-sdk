package io.embrace.android.embracesdk.assertions

import io.embrace.android.embracesdk.arch.assertError
import io.embrace.android.embracesdk.arch.assertIsKeySpan
import io.embrace.android.embracesdk.arch.assertIsPrivateSpan
import io.embrace.android.embracesdk.arch.assertNotKeySpan
import io.embrace.android.embracesdk.arch.assertNotPrivateSpan
import io.embrace.android.embracesdk.arch.assertSuccessful
import io.embrace.android.embracesdk.internal.clock.nanosToMillis
import io.embrace.android.embracesdk.internal.payload.Span
import io.embrace.android.embracesdk.internal.payload.SpanEvent
import io.embrace.android.embracesdk.internal.payload.toNewPayload
import io.embrace.android.embracesdk.internal.spans.EmbraceSpanData
import io.embrace.android.embracesdk.spans.EmbraceSpanEvent
import io.embrace.android.embracesdk.spans.ErrorCode
import io.opentelemetry.api.trace.StatusCode
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue

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
        assertEquals(expectedStartTimeMs, startTimeNanos.nanosToMillis())
        assertEquals(expectedEndTimeMs, endTimeNanos.nanosToMillis())
        assertEquals(expectedParentId, parentSpanId)
        if (expectedTraceId != null) {
            assertEquals(expectedTraceId, traceId)
        } else {
            assertEquals(32, traceId.length)
        }
        assertEquals(expectedStatus, status)
        if (errorCode == null) {
            assertSuccessful()
        } else {
            assertError(errorCode)
        }
        expectedCustomAttributes.forEach { entry ->
            assertEquals(entry.value, attributes[entry.key])
        }
        assertEquals(expectedEvents, events)
        if (private) {
            assertIsPrivateSpan()
        } else {
            assertNotPrivateSpan()
        }
        if (key) {
            assertIsKeySpan()
        } else {
            assertNotKeySpan()
        }
    }
}

internal fun Span.assertSpanPayload(
    expectedStartTimeMs: Long,
    expectedEndTimeMs: Long?,
    expectedParentId: String?,
    expectedTraceId: String? = null,
    errorCode: ErrorCode? = null,
    expectedCustomAttributes: Map<String, String> = emptyMap(),
    expectedEvents: List<SpanEvent> = emptyList(),
    private: Boolean = false,
    key: Boolean = false,
) {
    assertEquals(expectedStartTimeMs, startTimeUnixNano?.nanosToMillis())
    assertEquals(expectedEndTimeMs, endTimeUnixNano?.nanosToMillis())
    assertEquals(expectedParentId, parentSpanId)
    if (expectedTraceId != null) {
        assertEquals(expectedTraceId, traceId)
    } else {
        assertEquals(32, traceId?.length)
    }

    if (endTimeUnixNano == null) {
        assertEquals(Span.Status.UNSET, status)
    } else if (errorCode == null) {
        assertSuccessful()
    } else {
        assertError(errorCode)
    }

    val attributeSet = attributes?.toHashSet() ?: emptySet()
    expectedCustomAttributes.toNewPayload().forEach {
        assertTrue("$it is missing", attributeSet.contains(it))
    }

    assertArrayEquals(expectedEvents.toList().toTypedArray(), checkNotNull(events).toTypedArray())

    if (private) {
        assertIsPrivateSpan()
    } else {
        assertNotPrivateSpan()
    }

    if (key) {
        assertIsKeySpan()
    } else {
        assertNotKeySpan()
    }
}
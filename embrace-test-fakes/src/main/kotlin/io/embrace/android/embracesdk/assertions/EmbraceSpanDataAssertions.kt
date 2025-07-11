package io.embrace.android.embracesdk.assertions

import io.embrace.android.embracesdk.arch.assertError
import io.embrace.android.embracesdk.arch.assertIsPrivateSpan
import io.embrace.android.embracesdk.arch.assertNotPrivateSpan
import io.embrace.android.embracesdk.internal.clock.nanosToMillis
import io.embrace.android.embracesdk.internal.otel.schema.ErrorCodeAttribute
import io.embrace.android.embracesdk.internal.otel.sdk.findAttributeValue
import io.embrace.android.embracesdk.internal.otel.spans.EmbraceSpanData
import io.embrace.android.embracesdk.internal.payload.Span
import io.embrace.android.embracesdk.internal.payload.SpanEvent
import io.embrace.android.embracesdk.spans.ErrorCode
import io.opentelemetry.semconv.incubating.SessionIncubatingAttributes
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull

/**
 * Assert the [EmbraceSpanData] is as expected
 */
fun assertEmbraceSpanData(
    span: Span?,
    expectedStartTimeMs: Long,
    expectedEndTimeMs: Long?,
    expectedParentId: String,
    expectedTraceId: String? = null,
    expectedStatus: Span.Status = Span.Status.UNSET,
    expectedErrorCode: ErrorCode? = null,
    expectedCustomAttributes: Map<String, String> = emptyMap(),
    expectedEvents: List<SpanEvent> = emptyList(),
    expectedSessionId: String? = null,
    private: Boolean = false,
) {
    checkNotNull(span)
    with(span) {
        assertEquals("Wrong start time", expectedStartTimeMs, startTimeNanos?.nanosToMillis())
        assertEquals("Wrong end time", expectedEndTimeMs, endTimeNanos?.nanosToMillis())
        assertEquals(expectedParentId, parentSpanId)
        if (expectedTraceId != null) {
            assertEquals(expectedTraceId, traceId)
        } else {
            assertEquals(32, traceId?.length)
        }

        if (expectedErrorCode != null) {
            assertError(expectedErrorCode)
        } else {
            assertEquals(expectedStatus, status)
            assertNull(attributes?.findAttributeValue(ErrorCodeAttribute.Failure.key.name))
        }

        expectedCustomAttributes.forEach { entry ->
            assertEquals(entry.value, attributes?.findAttributeValue(entry.key))
        }
        assertEquals(expectedEvents, events)

        if (expectedSessionId != null) {
            assertEquals(expectedSessionId, attributes?.findAttributeValue(SessionIncubatingAttributes.SESSION_ID.key))
        }

        if (private) {
            assertIsPrivateSpan()
        } else {
            assertNotPrivateSpan()
        }
    }
}

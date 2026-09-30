package io.embrace.android.embracesdk.assertions

import io.embrace.android.embracesdk.internal.arch.schema.ErrorCodeAttribute
import io.embrace.android.embracesdk.internal.clock.nanosToMillis
import io.embrace.android.embracesdk.internal.otel.sdk.findAttributeValue
import io.embrace.android.embracesdk.internal.payload.Span
import io.embrace.android.embracesdk.internal.payload.SpanEvent
import io.embrace.android.embracesdk.semconv.EmbSessionAttributes
import io.embrace.android.embracesdk.spans.ErrorCode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull

/**
 * Assert the [Span] is as expected
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
    expectedUserSessionId: String? = null,
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
            assertNull(attributes?.findAttributeValue(ErrorCodeAttribute.Failure.key))
        }

        expectedCustomAttributes.forEach { entry ->
            assertEquals(entry.value, attributes?.findAttributeValue(entry.key))
        }
        // attribute order differs by OTel implementation and carries no meaning
        assertEquals(expectedEvents.map { it.withSortedAttributes() }, events?.map { it.withSortedAttributes() })

        if (expectedUserSessionId != null) {
            assertEquals(expectedUserSessionId, attributes?.findAttributeValue(EmbSessionAttributes.EMB_USER_SESSION_ID))
        }

        if (private) {
            assertIsPrivateSpan()
        } else {
            assertNotPrivateSpan()
        }
    }
}

/**
 * Returns a copy of the span with its attributes, and those of its events, sorted by key. Attribute order carries
 * no meaning and differs by OTel implementation, so use this to compare spans for equality.
 */
fun Span.withSortedAttributes(): Span = copy(
    attributes = attributes?.sortedBy { it.key },
    events = events?.map { it.withSortedAttributes() },
)

private fun SpanEvent.withSortedAttributes(): SpanEvent = copy(attributes = attributes?.sortedBy { it.key })

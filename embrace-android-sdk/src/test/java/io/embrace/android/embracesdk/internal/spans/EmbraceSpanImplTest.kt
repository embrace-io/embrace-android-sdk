package io.embrace.android.embracesdk.internal.spans

import io.embrace.android.embracesdk.fixtures.MAX_LENGTH_ATTRIBUTE_KEY
import io.embrace.android.embracesdk.fixtures.MAX_LENGTH_ATTRIBUTE_VALUE
import io.embrace.android.embracesdk.fixtures.MAX_LENGTH_EVENT_NAME
import io.embrace.android.embracesdk.fixtures.TOO_LONG_ATTRIBUTE_KEY
import io.embrace.android.embracesdk.fixtures.TOO_LONG_ATTRIBUTE_VALUE
import io.embrace.android.embracesdk.fixtures.TOO_LONG_EVENT_NAME
import io.embrace.android.embracesdk.fixtures.maxSizeEventAttributes
import io.embrace.android.embracesdk.fixtures.tooBigEventAttributes
import io.embrace.android.embracesdk.spans.EmbraceSpanEvent
import io.embrace.android.embracesdk.spans.ErrorCode
import io.opentelemetry.sdk.OpenTelemetrySdk
import io.opentelemetry.sdk.trace.SdkTracerProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

internal class EmbraceSpanImplTest {
    private lateinit var embraceSpan: EmbraceSpanImpl
    private lateinit var spanRepository: SpanRepository
    private val tracer = OpenTelemetrySdk.builder()
        .setTracerProvider(SdkTracerProvider.builder().build()).build()
        .getTracer(EmbraceSpanImplTest::class.java.name)

    @Before
    fun setup() {
        spanRepository = SpanRepository()
        embraceSpan = EmbraceSpanImpl(
            spanBuilder = tracer.spanBuilder("test-span"),
            spanRepository = spanRepository
        )
    }

    @Test
    fun `validate default state`() {
        with(embraceSpan) {
            assertNull(traceId)
            assertNull(spanId)
            assertFalse(isRecording)
            assertFalse(addEvent("eventName"))
            assertFalse(addAttribute("first", "value"))
            assertEquals(0, spanRepository.getActiveSpans().size)
            assertEquals(0, spanRepository.getCompletedSpans().size)
        }
    }

    @Test
    fun `validate span started state`() {
        with(embraceSpan) {
            assertTrue(start())
            assertFalse(start())
            assertNotNull(traceId)
            assertNotNull(spanId)
            assertTrue(isRecording)
            assertTrue(addEvent("eventName"))
            assertTrue(addAttribute("first", "value"))
            assertEquals(1, spanRepository.getActiveSpans().size)
            assertEquals(0, spanRepository.getCompletedSpans().size)
        }
    }

    @Test
    fun `validate span stopped state`() {
        with(embraceSpan) {
            assertTrue(start())
            assertTrue(stop())
            validateStoppedSpan()
        }
    }

    @Test
    fun `validate starting and stopping span with specific times`() {
        with(embraceSpan) {
            assertTrue(start(startTimeMs = 5L))
            assertTrue(stop(endTimeMs = 10L))
            validateStoppedSpan()
        }
    }

    @Test
    fun `validate usage without SpanRepository`() {
        with(EmbraceSpanImpl(spanBuilder = tracer.spanBuilder("test-span"))) {
            assertTrue(start())
            assertTrue(isRecording)
            assertTrue(stop())
            assertFalse(isRecording)
            assertNotNull(traceId)
            assertNotNull(spanId)
        }
    }

    @Test
    fun `check adding events`() {
        with(embraceSpan) {
            assertTrue(start())
            assertTrue(addEvent(name = "current event"))
            assertTrue(
                addEvent(
                    name = "second current event",
                    timestampMs = null,
                    attributes = mapOf(Pair("key", "value"), Pair("key2", "value1"))
                )
            )
            assertTrue(addEvent(name = "past event", timestampMs = 1L, attributes = null))
            assertTrue(addEvent(name = "future event", timestampMs = 2L, mapOf(Pair("key", "value"), Pair("key2", "value1"))))
        }
    }

    @Test
    fun `cannot stop twice irrespective of error code`() {
        with(embraceSpan) {
            assertTrue(start())
            assertTrue(stop(ErrorCode.FAILURE))
            assertFalse(stop())
            assertEquals(0, spanRepository.getActiveSpans().size)
            assertEquals(1, spanRepository.getCompletedSpans().size)
        }
    }

    @Test
    fun `check event limits`() {
        with(embraceSpan) {
            assertTrue(start())
            assertFalse(addEvent(name = TOO_LONG_EVENT_NAME))
            assertFalse(addEvent(name = TOO_LONG_EVENT_NAME, timestampMs = null, attributes = null))
            assertFalse(addEvent(name = "yo", timestampMs = null, attributes = tooBigEventAttributes))
            assertTrue(addEvent(name = MAX_LENGTH_EVENT_NAME))
            assertTrue(addEvent(name = MAX_LENGTH_EVENT_NAME, timestampMs = null, attributes = null))
            assertTrue(addEvent(name = "yo", timestampMs = null, attributes = maxSizeEventAttributes))
            repeat(EmbraceSpanImpl.MAX_EVENT_COUNT - 4) {
                assertTrue(addEvent(name = "event $it"))
            }
            val eventAttributesAMap = mutableMapOf(
                Pair(TOO_LONG_ATTRIBUTE_KEY, "value"),
                Pair("key", TOO_LONG_ATTRIBUTE_VALUE),
            )
            repeat(EmbraceSpanEvent.MAX_EVENT_ATTRIBUTE_COUNT - 2) {
                eventAttributesAMap["key$it"] = "value"
            }
            assertTrue(
                addEvent(
                    name = "yo",
                    timestampMs = null,
                    attributes = eventAttributesAMap
                )
            )
            assertFalse(addEvent("failed event"))
            assertTrue(stop())
        }
    }

    @Test
    fun `check attribute limits`() {
        with(embraceSpan) {
            assertTrue(start())
            assertFalse(addAttribute(key = TOO_LONG_ATTRIBUTE_KEY, value = "value"))
            assertFalse(addAttribute(key = "key", value = TOO_LONG_ATTRIBUTE_VALUE))
            assertTrue(addAttribute(key = MAX_LENGTH_ATTRIBUTE_KEY, value = "value"))
            assertTrue(addAttribute(key = "key", value = MAX_LENGTH_ATTRIBUTE_VALUE))
            assertTrue(addAttribute(key = "Key", value = MAX_LENGTH_ATTRIBUTE_VALUE))
            repeat(EmbraceSpanImpl.MAX_ATTRIBUTE_COUNT - 3) {
                assertTrue(addAttribute(key = "key$it", value = "value"))
            }
            assertFalse(addAttribute(key = "failedKey", value = "value"))
        }
    }

    private fun EmbraceSpanImpl.validateStoppedSpan() {
        assertFalse(stop())
        assertNotNull(traceId)
        assertNotNull(spanId)
        assertFalse(isRecording)
        assertFalse(addEvent("eventName"))
        assertFalse(addAttribute("first", "value"))
        assertEquals(0, spanRepository.getActiveSpans().size)
        assertEquals(1, spanRepository.getCompletedSpans().size)
    }
}

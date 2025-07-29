package io.embrace.android.embracesdk.internal.otel.impl

import io.embrace.android.embracesdk.fakes.FakeClock
import io.embrace.android.embracesdk.fakes.FakeEmbraceSdkSpan
import io.embrace.android.embracesdk.fakes.FakeOtelKotlinClock
import io.embrace.android.embracesdk.internal.clock.millisToNanos
import io.embrace.android.embracesdk.internal.config.instrumented.InstrumentedConfigImpl
import io.embrace.android.embracesdk.internal.otel.schema.ErrorCodeAttribute
import io.embrace.android.embracesdk.internal.otel.sdk.hasEmbraceAttribute
import io.embrace.android.embracesdk.internal.otel.toOtelKotlin
import io.embrace.opentelemetry.kotlin.Clock
import io.embrace.opentelemetry.kotlin.ExperimentalApi
import io.embrace.opentelemetry.kotlin.tracing.StatusCode
import io.embrace.opentelemetry.kotlin.tracing.recordException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalApi::class)
internal class EmbSpanTest {
    private lateinit var fakeClock: FakeClock
    private lateinit var openTelemetryClock: Clock
    private lateinit var fakeEmbraceSpan: FakeEmbraceSdkSpan
    private lateinit var embSpan: EmbSpan

    @Before
    fun setup() {
        fakeClock = FakeClock()
        openTelemetryClock = FakeOtelKotlinClock(fakeClock)
        fakeEmbraceSpan = FakeEmbraceSdkSpan.started(clock = fakeClock)
        embSpan = EmbSpan(
            impl = fakeEmbraceSpan,
            clock = openTelemetryClock
        )
    }

    @Test
    fun `validate started and stopped span`() {
        assertNotNull(embSpan.spanContext)
        assertTrue(embSpan.isRecording())
        with(fakeEmbraceSpan) {
            assertEquals(fakeClock.now(), spanStartTimeMs)
            assertNull(spanEndTimeMs)
        }
        val stopTime = fakeClock.tick()
        embSpan.end()
        assertFalse(embSpan.isRecording())
        with(fakeEmbraceSpan) {
            assertEquals(stopTime, spanEndTimeMs)
        }
    }

    @Test
    fun `specific end time used`() {
        with(embSpan) {
            fakeClock.tickSecond()
            val stopTime = fakeClock.now()
            end(stopTime.millisToNanos())
            assertFalse(isRecording())
            assertEquals(stopTime.millisToNanos(), fakeEmbraceSpan.snapshot()?.endTimeNanos)
        }
    }

    @Test
    fun `set error status before end`() {
        with(embSpan) {
            status = StatusCode.Error("error")
            end()
        }
        with(fakeEmbraceSpan) {
            assertTrue(status is StatusCode.Error)
            assertTrue(attributes.hasEmbraceAttribute(ErrorCodeAttribute.Failure))
        }
    }

    @Test
    fun `status can only be set on a span that is recording`() {
        with(embSpan) {
            end()
            status = StatusCode.Error("error")
            end()
        }

        with(fakeEmbraceSpan) {
            assertEquals(status, StatusCode.Unset)
            assertFalse(attributes.hasEmbraceAttribute(ErrorCodeAttribute.Failure))
        }
    }

    @Test
    fun `check adding events`() {
        val event1Time = openTelemetryClock.now()
        embSpan.addEvent("event1")
        fakeClock.tick(1)
        val event2Time = openTelemetryClock.now()
        embSpan.addEvent("event2") {
            setBooleanAttribute("boolean", true)
            setLongAttribute("integer", 1)
            setLongAttribute("long", 2L)
            setDoubleAttribute("double", 3.0)
            setStringAttribute("string", "value")
            setBooleanListAttribute("booleanArray", listOf(true, false))
            setLongListAttribute("integerArray", listOf(1, 2))
            setLongListAttribute("longArray", listOf(2L, 3L))
            setDoubleListAttribute("doubleArray", listOf(3.0, 4.0))
            setStringListAttribute("stringArray", listOf("value", "vee"))
        }
        with(checkNotNull(fakeEmbraceSpan.events)) {
            assertEquals(2, size)
            with(first()) {
                assertEquals("event1", name)
                assertEquals(event1Time, timestampNanos)
                assertEquals(0, attributes.size)
            }

            with(last()) {
                assertEquals("event2", name)
                assertEquals(event2Time, timestampNanos)
                assertEquals(10, attributes.size)
            }
        }
    }

    @Test
    fun `span name update`() {
        with(embSpan) {
            name = "new-name"
            assertEquals("new-name", fakeEmbraceSpan.name)
        }
    }

    @Test
    fun `recording exceptions as span events`() {
        val firstExceptionTime = openTelemetryClock.now()
        embSpan.recordException(IllegalStateException()) {}
        val secondExceptionTime = openTelemetryClock.now()
        embSpan.recordException(RuntimeException()) {
            setStringAttribute("myKey", "myValue")
        }

        with(checkNotNull(fakeEmbraceSpan.events)) {
            assertEquals(2, size)
            val expectedName = InstrumentedConfigImpl.otelLimits.getExceptionEventName()
            with(first()) {
                assertEquals(expectedName, name)
                assertEquals(firstExceptionTime, timestampNanos)
                assertEquals(2, attributes.size)
            }

            with(last()) {
                assertEquals(expectedName, name)
                assertEquals(secondExceptionTime, timestampNanos)
                assertEquals(3, attributes.size)
            }
        }
    }

    @Test
    fun `check adding and removing custom attributes`() {
        val attributesCount = fakeEmbraceSpan.attributes.size
        with(embSpan) {
            setBooleanAttribute("boolean", true)
            setLongAttribute("integer", 1)
            setLongAttribute("long", 2L)
            setDoubleAttribute("double", 3.0)
            setStringAttribute("string", "value")
            setBooleanListAttribute("booleanArray", listOf(true, false))
            setLongListAttribute("integerArray", listOf(1, 2))
            setLongListAttribute("longArray", listOf(2L, 3L))
            setDoubleListAttribute("doubleArray", listOf(3.0, 4.0))
            setStringListAttribute("stringArray", listOf("value", "vee"))
        }

        assertEquals(attributesCount + 10, fakeEmbraceSpan.attributes.size)
    }

    @Test
    fun `add span link`() {
        with(embSpan) {
            val linkedSpanContext = checkNotNull(FakeEmbraceSdkSpan.started().spanContext)
            addLink(linkedSpanContext.toOtelKotlin()) {
                setBooleanAttribute("boolean", true)
            }
            with(fakeEmbraceSpan.links.single()) {
                assertEquals(linkedSpanContext.spanId, spanContext.spanId)
                assertEquals(1, attributes.size)
                assertEquals("true", attributes["boolean"])
            }
        }
    }
}

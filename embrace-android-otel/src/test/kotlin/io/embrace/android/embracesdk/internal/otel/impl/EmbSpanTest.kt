package io.embrace.android.embracesdk.internal.otel.impl

import io.embrace.android.embracesdk.fakes.FakeClock
import io.embrace.android.embracesdk.fakes.FakeEmbraceSdkSpan
import io.embrace.android.embracesdk.fakes.FakeOpenTelemetryClock
import io.embrace.android.embracesdk.internal.config.instrumented.InstrumentedConfigImpl
import io.embrace.android.embracesdk.internal.otel.schema.ErrorCodeAttribute
import io.embrace.android.embracesdk.internal.otel.sdk.hasEmbraceAttribute
import io.embrace.android.embracesdk.internal.payload.Span
import io.embrace.opentelemetry.kotlin.aliases.OtelJavaAttributeKey
import io.embrace.opentelemetry.kotlin.aliases.OtelJavaAttributes
import io.embrace.opentelemetry.kotlin.aliases.OtelJavaClock
import io.embrace.opentelemetry.kotlin.aliases.OtelJavaStatusCode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.concurrent.TimeUnit

internal class EmbSpanTest {
    private lateinit var fakeClock: FakeClock
    private lateinit var openTelemetryClock: OtelJavaClock
    private lateinit var fakeEmbraceSpan: FakeEmbraceSdkSpan
    private lateinit var embSpan: EmbSpan

    @Before
    fun setup() {
        fakeClock = FakeClock()
        openTelemetryClock = FakeOpenTelemetryClock(fakeClock)
        fakeEmbraceSpan = FakeEmbraceSdkSpan.started(clock = fakeClock)
        embSpan = EmbSpan(
            embraceSpan = fakeEmbraceSpan,
            clock = openTelemetryClock
        )
    }

    @Test
    fun `validate started and stopped span`() {
        assertNotNull(embSpan.spanContext)
        assertTrue(embSpan.isRecording)
        with(fakeEmbraceSpan) {
            assertEquals(fakeClock.now(), spanStartTimeMs)
            assertNull(spanEndTimeMs)
        }
        val stopTime = fakeClock.tick()
        embSpan.end()
        assertFalse(embSpan.isRecording)
        with(fakeEmbraceSpan) {
            assertEquals(stopTime, spanEndTimeMs)
        }
    }

    @Test
    fun `specific end time used`() {
        with(embSpan) {
            val stopTimeSeconds = TimeUnit.MILLISECONDS.toSeconds(fakeClock.tickSecond())
            end(stopTimeSeconds, TimeUnit.SECONDS)
            assertFalse(isRecording)
            assertEquals(TimeUnit.SECONDS.toNanos(stopTimeSeconds), fakeEmbraceSpan.snapshot()?.endTimeNanos)
        }
    }

    @Test
    fun `set error status before end`() {
        with(embSpan) {
            setStatus(OtelJavaStatusCode.ERROR, "error")
            end()
        }
        with(fakeEmbraceSpan) {
            assertEquals(status, Span.Status.ERROR)
            assertTrue(attributes.hasEmbraceAttribute(ErrorCodeAttribute.Failure))
        }
    }

    @Test
    fun `status can only be set on a span that is recording`() {
        with(embSpan) {
            end()
            setStatus(OtelJavaStatusCode.ERROR, "error")
            end()
        }

        with(fakeEmbraceSpan) {
            assertEquals(status, Span.Status.UNSET)
            assertFalse(attributes.hasEmbraceAttribute(ErrorCodeAttribute.Failure))
        }
    }

    @Test
    fun `check adding events`() {
        val attributesBuilder =
            OtelJavaAttributes
                .builder()
                .put("boolean", true)
                .put("integer", 1)
                .put("long", 2L)
                .put("double", 3.0)
                .put("string", "value")
                .put("booleanArray", true, false)
                .put("integerArray", 1, 2)
                .put("longArray", 2L, 3L)
                .put("doubleArray", 3.0, 4.0)
                .put("stringArray", "value", "vee")

        val event1Time = openTelemetryClock.now()
        embSpan.addEvent("event1")
        fakeClock.tick(1)
        val event2Time = openTelemetryClock.now()
        embSpan.addEvent("event2", attributesBuilder.build())
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
            updateName("new-name")
            assertEquals("new-name", fakeEmbraceSpan.name)
        }
    }

    @Test
    fun `recording exceptions as span events`() {
        val firstExceptionTime = openTelemetryClock.now()
        embSpan.recordException(IllegalStateException())
        val secondExceptionTime = openTelemetryClock.now()
        embSpan.recordException(RuntimeException(), OtelJavaAttributes.builder().put("myKey", "myValue").build())

        with(checkNotNull(fakeEmbraceSpan.events)) {
            assertEquals(2, size)
            val expectedName = InstrumentedConfigImpl.otelLimits.getExceptionEventName()
            with(first()) {
                assertEquals(expectedName, name)
                assertEquals(firstExceptionTime, timestampNanos)
                assertEquals(0, attributes.size)
            }

            with(last()) {
                assertEquals(expectedName, name)
                assertEquals(secondExceptionTime, timestampNanos)
                assertEquals(1, attributes.size)
            }
        }
    }

    @Test
    fun `check adding and removing custom attributes`() {
        val attributesCount = fakeEmbraceSpan.attributes.size
        with(embSpan) {
            setAttribute("boolean", true)
            setAttribute("integer", 1)
            setAttribute("long", 2L)
            setAttribute("double", 3.0)
            setAttribute("string", "value")
            setAttribute(OtelJavaAttributeKey.booleanArrayKey("booleanArray"), listOf(true, false))
            setAttribute(OtelJavaAttributeKey.longArrayKey("integerArray"), listOf(1, 2))
            setAttribute(OtelJavaAttributeKey.longArrayKey("longArray"), listOf(2L, 3L))
            setAttribute(OtelJavaAttributeKey.doubleArrayKey("doubleArray"), listOf(3.0, 4.0))
            setAttribute(OtelJavaAttributeKey.stringArrayKey("stringArray"), listOf("value", "vee"))
        }

        assertEquals(attributesCount + 10, fakeEmbraceSpan.attributes.size)
    }

    @Test
    fun `add span link`() {
        with(embSpan) {
            val linkedSpanContext = checkNotNull(FakeEmbraceSdkSpan.started().spanContext)
            addLink(linkedSpanContext, OtelJavaAttributes.builder().put("boolean", true).build())
            with(fakeEmbraceSpan.links.single()) {
                assertEquals(linkedSpanContext.spanId, spanContext.spanId)
                assertEquals(1, attributes.size)
                assertEquals("true", attributes["boolean"])
            }
        }
    }
}

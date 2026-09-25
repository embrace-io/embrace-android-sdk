package io.embrace.android.embracesdk.internal.otel.impl

import io.embrace.android.embracesdk.fakes.FakeClock
import io.embrace.android.embracesdk.fakes.FakeEmbraceSdkSpan
import io.embrace.android.embracesdk.fakes.FakeOtelKotlinClock
import io.embrace.android.embracesdk.fakes.FakeTelemetryService
import io.embrace.android.embracesdk.fakes.TestConstants.TESTS_DEFAULT_USE_KOTLIN_SDK
import io.embrace.android.embracesdk.fakes.fakeOpenTelemetry
import io.embrace.android.embracesdk.internal.arch.schema.EmbType
import io.embrace.android.embracesdk.internal.arch.schema.ErrorCodeAttribute
import io.embrace.android.embracesdk.internal.clock.millisToNanos
import io.embrace.android.embracesdk.internal.config.instrumented.InstrumentedConfigImpl
import io.embrace.android.embracesdk.internal.otel.createSdkOtelInstance
import io.embrace.android.embracesdk.internal.otel.sdk.DataValidator
import io.embrace.android.embracesdk.internal.otel.sdk.hasEmbraceAttribute
import io.embrace.android.embracesdk.internal.otel.spans.EmbraceSpanFactoryImpl
import io.embrace.android.embracesdk.internal.otel.spans.OtelSpanStartArgs
import io.embrace.android.embracesdk.internal.otel.spans.SpanRepository
import io.opentelemetry.kotlin.Clock
import io.opentelemetry.kotlin.attributes.AnyValue
import io.opentelemetry.kotlin.getTracer
import io.opentelemetry.kotlin.tracing.StatusData
import io.opentelemetry.kotlin.tracing.recordException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

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
            clock = openTelemetryClock,
            openTelemetry = fakeOpenTelemetry(),
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
            setStatus(StatusData.Error("error"))
            end()
        }
        with(fakeEmbraceSpan) {
            assertTrue(status is StatusData.Error)
            assertTrue(attributes.hasEmbraceAttribute(ErrorCodeAttribute.Failure))
        }
    }

    @Test
    fun `status can only be set on a span that is recording`() {
        with(embSpan) {
            end()
            setStatus(StatusData.Error("error"))
            end()
        }

        with(fakeEmbraceSpan) {
            assertEquals(status, StatusData.Unset)
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
                assertEquals(
                    mapOf(
                        "boolean" to "true",
                        "integer" to "1",
                        "long" to "2",
                        "double" to "3.0",
                        "string" to "value",
                        "booleanArray" to "[true, false]",
                        "integerArray" to "[1, 2]",
                        "longArray" to "[2, 3]",
                        "doubleArray" to "[3.0, 4.0]",
                        "stringArray" to "[value, vee]",
                    ),
                    attributes,
                )
            }
        }
    }

    @Test
    fun `event attributes over the limit are truncated to the max event attribute count`() {
        val dataValidator = DataValidator(telemetryService = FakeTelemetryService())
        val tracer = createSdkOtelInstance(useKotlinSdk = TESTS_DEFAULT_USE_KOTLIN_SDK, clock = openTelemetryClock)
            .getTracer("test-tracer")
        val realSpan = EmbraceSpanFactoryImpl(
            openTelemetryClock = openTelemetryClock,
            spanRepository = SpanRepository(),
            dataValidator = dataValidator,
            telemetryService = FakeTelemetryService(),
        ).create(
            OtelSpanStartArgs(
                name = "test-span",
                type = EmbType.Performance.Default,
                internal = false,
                private = false,
                tracer = tracer,
                openTelemetry = fakeOpenTelemetry(),
            ),
        )
        assertTrue(realSpan.start())
        val realEmbSpan = EmbSpan(
            impl = realSpan,
            clock = openTelemetryClock,
            openTelemetry = fakeOpenTelemetry(),
        )
        val max = dataValidator.otelLimitsConfig.getMaxEventAttributeCount()

        realEmbSpan.addEvent("event") {
            repeat(max + 5) {
                setLongAttribute("key$it", it.toLong())
            }
        }

        val attributes = checkNotNull(realSpan.events().single().attributes)
        assertEquals(max, attributes.size)
        attributes.forEach {
            assertEquals(checkNotNull(it.key).removePrefix("key"), it.data)
        }
    }

    @Test
    fun `span name update`() {
        with(embSpan) {
            setName("new-name")
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
            addLink(linkedSpanContext) {
                setBooleanAttribute("boolean", true)
            }
            with(fakeEmbraceSpan.links.single()) {
                assertEquals(linkedSpanContext.spanId, spanContext.spanId)
                assertEquals(1, attributes.size)
                assertEquals("true", attributes["boolean"])
            }
        }
    }

    @Test
    fun `any value and byte array attributes are serialized by content`() {
        with(embSpan) {
            setAnyValueAttribute("any", AnyValue.LongValue(3L))
            addEvent("event") {
                setAnyValueAttribute("any", AnyValue.StringValue("wrapped"))
                setByteArrayAttribute("bytes", byteArrayOf(1, 2))
            }
            addLink(checkNotNull(FakeEmbraceSdkSpan.started().spanContext)) {
                setAnyValueAttribute("any", AnyValue.BoolValue(true))
                setByteArrayAttribute("bytes", byteArrayOf(1, 2))
            }
        }
        assertEquals("3", fakeEmbraceSpan.attributes["any"])
        assertEquals(mapOf("any" to "wrapped", "bytes" to "[1, 2]"), fakeEmbraceSpan.events.single().attributes)
        assertEquals(mapOf("any" to "true", "bytes" to "[1, 2]"), fakeEmbraceSpan.links.single().attributes)
    }
}

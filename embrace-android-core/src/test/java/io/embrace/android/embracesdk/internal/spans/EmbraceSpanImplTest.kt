package io.embrace.android.embracesdk.internal.spans

import io.embrace.android.embracesdk.fakes.FakeClock
import io.embrace.android.embracesdk.fakes.FakeOpenTelemetryClock
import io.embrace.android.embracesdk.fakes.injection.FakeInitModule
import io.embrace.android.embracesdk.fixtures.MAX_LENGTH_ATTRIBUTE_KEY
import io.embrace.android.embracesdk.fixtures.MAX_LENGTH_ATTRIBUTE_VALUE
import io.embrace.android.embracesdk.fixtures.MAX_LENGTH_EVENT_NAME
import io.embrace.android.embracesdk.fixtures.TOO_LONG_ATTRIBUTE_KEY
import io.embrace.android.embracesdk.fixtures.TOO_LONG_ATTRIBUTE_VALUE
import io.embrace.android.embracesdk.fixtures.TOO_LONG_EVENT_NAME
import io.embrace.android.embracesdk.fixtures.TOO_LONG_SPAN_NAME
import io.embrace.android.embracesdk.fixtures.fakeContextKey
import io.embrace.android.embracesdk.fixtures.maxSizeEventAttributes
import io.embrace.android.embracesdk.fixtures.tooBigEventAttributes
import io.embrace.android.embracesdk.internal.arch.schema.EmbType
import io.embrace.android.embracesdk.internal.arch.schema.PrivateSpan
import io.embrace.android.embracesdk.internal.clock.millisToNanos
import io.embrace.android.embracesdk.internal.clock.nanosToMillis
import io.embrace.android.embracesdk.internal.config.behavior.REDACTED_LABEL
import io.embrace.android.embracesdk.internal.config.behavior.SensitiveKeysBehaviorImpl
import io.embrace.android.embracesdk.internal.opentelemetry.embraceSpanBuilder
import io.embrace.android.embracesdk.internal.payload.Span
import io.embrace.android.embracesdk.internal.serialization.PlatformSerializer
import io.embrace.android.embracesdk.internal.spans.EmbraceSpanLimits.MAX_CUSTOM_ATTRIBUTE_COUNT
import io.embrace.android.embracesdk.internal.utils.truncatedStacktraceText
import io.embrace.android.embracesdk.spans.ErrorCode
import io.opentelemetry.api.trace.SpanId
import io.opentelemetry.sdk.OpenTelemetrySdk
import io.opentelemetry.sdk.common.Clock
import io.opentelemetry.sdk.trace.SdkTracerProvider
import io.opentelemetry.semconv.ExceptionAttributes
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

internal class EmbraceSpanImplTest {
    private lateinit var fakeClock: FakeClock
    private lateinit var openTelemetryClock: Clock
    private lateinit var embraceSpan: EmbraceSpanImpl
    private lateinit var spanRepository: SpanRepository
    private lateinit var serializer: PlatformSerializer
    private val tracer = OpenTelemetrySdk.builder()
        .setTracerProvider(SdkTracerProvider.builder().build()).build()
        .getTracer(EmbraceSpanImplTest::class.java.name)

    private val sensitiveKeysBehavior = SensitiveKeysBehaviorImpl(listOf("password"))

    @Before
    fun setup() {
        fakeClock = FakeClock()
        val fakeInitModule = FakeInitModule(fakeClock)
        openTelemetryClock = fakeInitModule.openTelemetryModule.openTelemetryClock
        spanRepository = SpanRepository()
        serializer = fakeInitModule.jsonSerializer
        embraceSpan = createEmbraceSpanImpl(
            spanBuilder = tracer.embraceSpanBuilder(
                name = EXPECTED_SPAN_NAME,
                type = EmbType.Performance.Default,
                internal = false,
                private = false
            )
        )
        fakeClock.tick(100)
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
            assertNull(embraceSpan.snapshot())
        }
    }

    @Test
    fun `validate span started state`() {
        with(embraceSpan) {
            assertTrue(start())
            val expectedStartTimeMs = fakeClock.now()
            assertFalse(start())
            assertNotNull(traceId)
            assertNotNull(spanId)
            assertTrue(isRecording)
            assertSnapshot(expectedStartTimeMs = expectedStartTimeMs, expectedEndTimeMs = null)
            assertTrue(addEvent("eventName"))
            assertTrue(addAttribute("first", "value"))
            assertSnapshot(
                expectedStartTimeMs = expectedStartTimeMs,
                expectedEndTimeMs = null,
                eventCount = 1,
                expectedCustomAttributeCount = 1
            )
            assertEquals(1, spanRepository.getActiveSpans().size)
            assertEquals(0, spanRepository.getCompletedSpans().size)
        }
    }

    @Test
    fun `validate span stopped state`() {
        with(embraceSpan) {
            assertTrue(start())
            val expectedStartTimeMs = fakeClock.now()
            fakeClock.tick(200)
            assertTrue(stop())
            val expectedEndTimeMs = fakeClock.now()
            assertSnapshot(
                expectedStartTimeMs = expectedStartTimeMs,
                expectedEndTimeMs = expectedEndTimeMs,
            )
            validateStoppedSpan()
        }
    }

    @Test
    fun `validate starting and stopping span with specific times`() {
        with(embraceSpan) {
            val expectedStartTimeMs = fakeClock.now() + 1000
            val expectedEndTimeMs = fakeClock.now() + 5700
            assertTrue(start(startTimeMs = expectedStartTimeMs))
            assertTrue(stop(errorCode = ErrorCode.FAILURE, endTimeMs = expectedEndTimeMs))
            assertSnapshot(
                expectedStartTimeMs = expectedStartTimeMs,
                expectedEndTimeMs = expectedEndTimeMs,
                expectedStatus = Span.Status.ERROR
            )
            validateStoppedSpan()
        }
    }

    @Test
    fun `starting and stopping span with nanosecond timestamps`() {
        with(embraceSpan) {
            val expectedStartTimeMs = fakeClock.now() + 99
            val expectedEndTimeMs = fakeClock.now() + 505
            assertTrue(start(startTimeMs = expectedStartTimeMs.millisToNanos()))
            assertTrue(stop(errorCode = ErrorCode.FAILURE, endTimeMs = expectedEndTimeMs.millisToNanos()))
            assertSnapshot(
                expectedStartTimeMs = expectedStartTimeMs,
                expectedEndTimeMs = expectedEndTimeMs,
                expectedStatus = Span.Status.ERROR
            )
            validateStoppedSpan()
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
            assertTrue(addEvent(name = "past event", timestampMs = fakeClock.now() - 1L, attributes = null))
            assertTrue(
                addEvent(name = "future event", timestampMs = fakeClock.now() + 2L, mapOf(Pair("key", "value"), Pair("key2", "value1")))
            )
        }
    }

    @Test
    fun `span name update`() {
        with(embraceSpan) {
            assertTrue(embraceSpan.updateName("new-name"))
            assertFalse(embraceSpan.updateName(TOO_LONG_SPAN_NAME))
            assertFalse(embraceSpan.updateName(""))
            assertFalse(embraceSpan.updateName(" "))
            assertTrue(start())
            assertEquals("new-name", embraceSpan.snapshot()?.name)
            assertTrue(embraceSpan.updateName("new-new-name"))
            assertEquals("new-new-name", embraceSpan.snapshot()?.name)
            assertTrue(stop())
            assertFalse(embraceSpan.updateName("failed-to-update"))
            assertEquals("new-new-name", embraceSpan.snapshot()?.name)
        }
    }

    @Test
    fun `recording exceptions as span events`() {
        val timestampNanos = openTelemetryClock.now()
        val firstException = IllegalStateException("oops")
        val firstExceptionStackTrace = firstException.truncatedStacktraceText()
        val secondException = RuntimeException("haha", firstException)
        val secondExceptionStackTrace = secondException.truncatedStacktraceText()

        with(embraceSpan) {
            assertFalse(recordException(exception = IllegalStateException()))
            assertTrue(start())
            assertTrue(recordException(exception = firstException))
            assertTrue(recordException(exception = secondException, attributes = mapOf("myKey" to "myValue")))
            assertFalse(recordException(exception = RuntimeException(), attributes = tooBigEventAttributes))
            assertTrue(stop())
            assertFalse(recordException(exception = IllegalStateException()))
        }

        with(checkNotNull(embraceSpan.snapshot())) {
            val sanitizedEvents = checkNotNull(events)
            assertEquals(2, sanitizedEvents.size)
            with(sanitizedEvents.first()) {
                assertEquals(EmbraceSpanLimits.EXCEPTION_EVENT_NAME, name)
                val attrs = checkNotNull(attributes)
                assertEquals(timestampNanos, timestampNanos)
                assertEquals(
                    IllegalStateException::class.java.canonicalName,
                    attrs.single { it.key == ExceptionAttributes.EXCEPTION_TYPE.key }.data
                )
                assertEquals("oops", attrs.single { it.key == ExceptionAttributes.EXCEPTION_MESSAGE.key }.data)
                assertEquals(
                    firstExceptionStackTrace,
                    attrs.single { it.key == ExceptionAttributes.EXCEPTION_STACKTRACE.key }.data
                )
            }
            with(sanitizedEvents.last()) {
                assertEquals(EmbraceSpanLimits.EXCEPTION_EVENT_NAME, name)
                val attrs = checkNotNull(attributes)
                assertEquals(timestampNanos, timestampNanos)
                assertEquals(
                    RuntimeException::class.java.canonicalName,
                    attrs.single { it.key == ExceptionAttributes.EXCEPTION_TYPE.key }.data
                )
                assertEquals("haha", attrs.single { it.key == ExceptionAttributes.EXCEPTION_MESSAGE.key }.data)
                assertEquals("myValue", attrs.single { it.key == "myKey" }.data)
                assertEquals(
                    secondExceptionStackTrace,
                    attrs.single { it.key == ExceptionAttributes.EXCEPTION_STACKTRACE.key }.data
                )
            }
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
            assertTrue(recordException(exception = RuntimeException()))
            repeat(EmbraceSpanLimits.MAX_CUSTOM_EVENT_COUNT - 5) {
                assertTrue(addEvent(name = "event $it"))
            }
            val eventAttributesAMap = mutableMapOf(
                Pair(TOO_LONG_ATTRIBUTE_KEY, "value"),
                Pair("key", TOO_LONG_ATTRIBUTE_VALUE),
            )
            repeat(8) {
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
            assertFalse(recordException(exception = RuntimeException()))
            assertTrue(stop())
        }
    }

    @Test
    fun `check adding and removing system attributes not affected by custom attributes`() {
        with(embraceSpan) {
            assertTrue(start())
            repeat(MAX_CUSTOM_ATTRIBUTE_COUNT) {
                assertTrue(addAttribute(key = "key$it", value = "value"))
            }
            assertFalse(addAttribute(key = "failed", value = "value"))
            addSystemAttribute("system-attribute", "value")
            assertEquals("value", embraceSpan.snapshot()?.attributes?.findAttributeValue("system-attribute"))
            removeSystemAttribute("system-attribute")
            assertNull("value", embraceSpan.snapshot()?.attributes?.findAttributeValue("system-attribute"))
        }
    }

    @Test
    fun `check custom attribute limits`() {
        with(embraceSpan) {
            assertTrue(start())
            assertFalse(addAttribute(key = TOO_LONG_ATTRIBUTE_KEY, value = "value"))
            assertFalse(addAttribute(key = "key", value = TOO_LONG_ATTRIBUTE_VALUE))
            assertTrue(addAttribute(key = MAX_LENGTH_ATTRIBUTE_KEY, value = "value"))
            assertTrue(addAttribute(key = "key", value = MAX_LENGTH_ATTRIBUTE_VALUE))
            assertTrue(addAttribute(key = "Key", value = MAX_LENGTH_ATTRIBUTE_VALUE))
            repeat(MAX_CUSTOM_ATTRIBUTE_COUNT - 3) {
                assertTrue(addAttribute(key = "key$it", value = "value"))
            }
            assertFalse(addAttribute(key = "failedKey", value = "value"))
        }
    }

    @Test
    fun `validate full snapshot`() {
        embraceSpan = createEmbraceSpanImpl(
            spanBuilder = createEmbraceSpanBuilder()
        )
        assertTrue(embraceSpan.start())
        assertNotNull(embraceSpan.snapshot())
        with(embraceSpan) {
            val expectedStartTimeMs = fakeClock.now()
            fakeClock.tick(100)
            val expectedEventTime = fakeClock.now()
            assertTrue(
                addEvent(
                    name = EXPECTED_EVENT_NAME,
                    timestampMs = null,
                    attributes = mapOf(Pair(EXPECTED_ATTRIBUTE_NAME, EXPECTED_ATTRIBUTE_VALUE))
                )
            )
            assertTrue(addAttribute(key = EXPECTED_ATTRIBUTE_NAME, value = EXPECTED_ATTRIBUTE_VALUE))

            val snapshot = checkNotNull(embraceSpan.snapshot())

            assertEquals(traceId, snapshot.traceId)
            assertEquals(spanId, snapshot.spanId)
            assertEquals(SpanId.getInvalid(), snapshot.parentSpanId)
            assertEquals(EXPECTED_SPAN_NAME.toEmbraceObjectName(), snapshot.name)
            assertTrue(hasFixedAttribute(EmbType.System.LowPower))
            assertTrue(hasFixedAttribute(PrivateSpan))
            assertEquals(expectedStartTimeMs.millisToNanos(), snapshot.startTimeNanos)
            assertEquals(Span.Status.UNSET, snapshot.status)

            val snapshotEvent = checkNotNull(snapshot.events).single()
            assertEquals(EXPECTED_EVENT_NAME, snapshotEvent.name)
            assertEquals(expectedEventTime.millisToNanos(), snapshotEvent.timestampNanos)

            val eventAttributes = checkNotNull(snapshotEvent.attributes).single { !checkNotNull(it.key).startsWith("emb.") }
            assertEquals(EXPECTED_ATTRIBUTE_NAME, eventAttributes.key)
            assertEquals(EXPECTED_ATTRIBUTE_VALUE, eventAttributes.data)

            val snapshotAttributes = checkNotNull(snapshot.attributes).single { !checkNotNull(it.key).startsWith("emb.") }
            assertEquals(EXPECTED_ATTRIBUTE_NAME, snapshotAttributes.key)
            assertEquals(EXPECTED_ATTRIBUTE_VALUE, snapshotAttributes.data)
        }
    }

    @Test
    fun `start time from span start method overrides all`() {
        val spanBuilder = createEmbraceSpanBuilder()
        spanBuilder.startTimeMs = fakeClock.tick()
        embraceSpan = createEmbraceSpanImpl(spanBuilder)

        val timePassedIn = fakeClock.tick()
        fakeClock.tick()
        assertTrue(embraceSpan.start(startTimeMs = timePassedIn))
        assertEquals(timePassedIn, embraceSpan.snapshot()?.startTimeNanos?.nanosToMillis())
    }

    @Test
    fun `OTel clock used if start time passed is zero`() {
        val fakeOpenTelemetryClock = FakeOpenTelemetryClock(fakeClock)
        val spanBuilder = createEmbraceSpanBuilder()
        embraceSpan = createEmbraceSpanImpl(spanBuilder, fakeOpenTelemetryClock)

        assertTrue(embraceSpan.start(startTimeMs = 0L))
        assertEquals(fakeOpenTelemetryClock.now(), embraceSpan.snapshot()?.startTimeNanos)
    }

    @Test
    fun `start time from span builder used if no start time passed into start method`() {
        val spanBuilder = createEmbraceSpanBuilder()

        val timeOnSpanBuilder = fakeClock.tick()
        spanBuilder.startTimeMs = timeOnSpanBuilder
        embraceSpan = createEmbraceSpanImpl(spanBuilder)
        fakeClock.tick()
        assertTrue(embraceSpan.start())
        assertEquals(timeOnSpanBuilder, embraceSpan.snapshot()?.startTimeNanos?.nanosToMillis())
    }

    @Test
    fun `validate context objects are propagated from the parent to the child span`() {
        val spanBuilder = createEmbraceSpanBuilder()
        val newParentContext = spanBuilder.parentContext.with(fakeContextKey, "fake-value")
        spanBuilder.setParentContext(newParentContext)

        embraceSpan = createEmbraceSpanImpl(spanBuilder)

        assertNull(embraceSpan.asNewContext())
        assertTrue(embraceSpan.start())
        val newSpanContext = checkNotNull(embraceSpan.asNewContext())
        assertEquals("fake-value", newSpanContext.get(fakeContextKey))
    }

    @Test
    fun `custom attributes are redacted if their key is sensitive when getting a span snapshot`() {
        // given a span with a sensitive key
        val spanBuilder = createEmbraceSpanBuilder()
        spanBuilder.setCustomAttribute("password", "123456")
        spanBuilder.setCustomAttribute("status", "ok")
        embraceSpan = createEmbraceSpanImpl(spanBuilder)
        embraceSpan.start()

        // when getting a span snapshot
        val snapshot = embraceSpan.snapshot()

        // then the sensitive keys should be redacted
        assertTrue(snapshot?.attributes?.any { it.key == "password" && it.data == REDACTED_LABEL } ?: false)
        assertTrue(snapshot?.attributes?.any { it.key == "status" && it.data == "ok" } ?: false)
    }

    @Test
    fun `event attributes are redacted if their key is sensitive when getting a span snapshot`() {
        // given a span event with a sensitive key
        val spanBuilder = createEmbraceSpanBuilder()
        embraceSpan = createEmbraceSpanImpl(spanBuilder)
        embraceSpan.start()
        embraceSpan.addEvent("event", null, mapOf("password" to "123456", "status" to "ok"))
        embraceSpan.addEvent("anotherEvent", null, mapOf("password" to "654321", "someKey" to "someValue"))

        // when getting a span snapshot
        val snapshot = embraceSpan.snapshot()

        // then the sensitive keys should be redacted
        val event = snapshot?.events?.first { it.name == "event" }
        val anotherEvent = snapshot?.events?.first { it.name == "anotherEvent" }
        assertTrue(event?.attributes?.any { it.key == "password" && it.data == REDACTED_LABEL } ?: false)
        assertTrue(event?.attributes?.any { it.key == "status" && it.data == "ok" } ?: false)
        assertTrue(anotherEvent?.attributes?.any { it.key == "password" && it.data == REDACTED_LABEL } ?: false)
        assertTrue(anotherEvent?.attributes?.any { it.key == "someKey" && it.data == "someValue" } ?: false)
    }

    private fun createEmbraceSpanBuilder() = tracer.embraceSpanBuilder(
        name = EXPECTED_SPAN_NAME,
        type = EmbType.System.LowPower,
        internal = true,
        private = true
    )

    private fun createEmbraceSpanImpl(
        spanBuilder: EmbraceSpanBuilder,
        clock: Clock = openTelemetryClock
    ) = EmbraceSpanImpl(
        spanBuilder = spanBuilder,
        openTelemetryClock = clock,
        spanRepository = spanRepository,
        sensitiveKeysBehavior = sensitiveKeysBehavior
    )

    private fun EmbraceSpanImpl.assertSnapshot(
        expectedStartTimeMs: Long,
        expectedEndTimeMs: Long? = null,
        expectedType: EmbType = EmbType.Performance.Default,
        expectedStatus: Span.Status = Span.Status.UNSET,
        eventCount: Int = 0,
        expectedEmbraceAttributes: Int = 2,
        expectedCustomAttributeCount: Int = 0,
        isPrivate: Boolean = false,
    ) {
        with(checkNotNull(snapshot())) {
            assertEquals(expectedStartTimeMs, startTimeNanos?.nanosToMillis())
            assertEquals(expectedEndTimeMs, endTimeNanos?.nanosToMillis())
            assertEquals(expectedStatus, status)
            assertEquals(eventCount, events?.size)
            assertTrue(hasFixedAttribute(expectedType))
            assertEquals(isPrivate, hasFixedAttribute(PrivateSpan))
            val attrs = checkNotNull(attributes)
            val embraceAttributeCount = attrs.filter { checkNotNull(it.key).startsWith("emb.") }.size
            val customAttributeCount = attrs.size - embraceAttributeCount
            assertEquals(expectedEmbraceAttributes, embraceAttributeCount)
            assertEquals(expectedCustomAttributeCount, customAttributeCount)
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

    companion object {
        private const val EXPECTED_SPAN_NAME = "test-span"
        private const val EXPECTED_EVENT_NAME = "fun event 🔥"
        private const val EXPECTED_ATTRIBUTE_NAME = "attribute-key"
        private const val EXPECTED_ATTRIBUTE_VALUE = "harharhar"
    }
}

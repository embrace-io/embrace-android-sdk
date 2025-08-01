package io.embrace.android.embracesdk.internal.otel.spans

import io.embrace.android.embracesdk.assertions.validateLinkToSpan
import io.embrace.android.embracesdk.assertions.validateSystemLink
import io.embrace.android.embracesdk.fakes.FakeClock
import io.embrace.android.embracesdk.fakes.FakeEmbraceSdkSpan
import io.embrace.android.embracesdk.fakes.FakeOtelKotlinClock
import io.embrace.android.embracesdk.fakes.TestPlatformSerializer
import io.embrace.android.embracesdk.fakes.fakeObjectCreator
import io.embrace.android.embracesdk.fixtures.MAX_LENGTH_ATTRIBUTE_KEY
import io.embrace.android.embracesdk.fixtures.MAX_LENGTH_ATTRIBUTE_KEY_FOR_INTERNAL_SPAN
import io.embrace.android.embracesdk.fixtures.MAX_LENGTH_ATTRIBUTE_VALUE
import io.embrace.android.embracesdk.fixtures.MAX_LENGTH_ATTRIBUTE_VALUE_FOR_INTERNAL_SPAN
import io.embrace.android.embracesdk.fixtures.MAX_LENGTH_EVENT_NAME
import io.embrace.android.embracesdk.fixtures.TOO_LONG_ATTRIBUTE_KEY
import io.embrace.android.embracesdk.fixtures.TOO_LONG_ATTRIBUTE_KEY_FOR_INTERNAL_SPAN
import io.embrace.android.embracesdk.fixtures.TOO_LONG_ATTRIBUTE_VALUE
import io.embrace.android.embracesdk.fixtures.TOO_LONG_ATTRIBUTE_VALUE_FOR_INTERNAL_SPAN
import io.embrace.android.embracesdk.fixtures.TOO_LONG_EVENT_NAME
import io.embrace.android.embracesdk.fixtures.TOO_LONG_SPAN_NAME
import io.embrace.android.embracesdk.fixtures.TRUNCATED_TOO_LONG_ATTRIBUTE_KEY
import io.embrace.android.embracesdk.fixtures.TRUNCATED_TOO_LONG_ATTRIBUTE_KEY_FOR_INTERNAL_SPAN
import io.embrace.android.embracesdk.fixtures.TRUNCATED_TOO_LONG_ATTRIBUTE_VALUE
import io.embrace.android.embracesdk.fixtures.TRUNCATED_TOO_LONG_ATTRIBUTE_VALUE_FOR_INTERNAL_SPAN
import io.embrace.android.embracesdk.fixtures.TRUNCATED_TOO_LONG_EVENT_NAME
import io.embrace.android.embracesdk.fixtures.TRUNCATED_TOO_LONG_SPAN_NAME
import io.embrace.android.embracesdk.fixtures.fakeContextKey
import io.embrace.android.embracesdk.fixtures.maxSizeEventAttributes
import io.embrace.android.embracesdk.fixtures.tooBigEventAttributes
import io.embrace.android.embracesdk.internal.clock.millisToNanos
import io.embrace.android.embracesdk.internal.clock.nanosToMillis
import io.embrace.android.embracesdk.internal.otel.schema.EmbType
import io.embrace.android.embracesdk.internal.otel.schema.LinkType
import io.embrace.android.embracesdk.internal.otel.schema.PrivateSpan
import io.embrace.android.embracesdk.internal.otel.sdk.DataValidator
import io.embrace.android.embracesdk.internal.otel.sdk.findAttributeValue
import io.embrace.android.embracesdk.internal.otel.sdk.id.OtelIds
import io.embrace.android.embracesdk.internal.otel.sdk.toEmbraceObjectName
import io.embrace.android.embracesdk.internal.payload.Span
import io.embrace.android.embracesdk.internal.serialization.PlatformSerializer
import io.embrace.android.embracesdk.internal.utils.truncatedStacktraceText
import io.embrace.android.embracesdk.spans.ErrorCode
import io.embrace.opentelemetry.kotlin.ExperimentalApi
import io.embrace.opentelemetry.kotlin.OpenTelemetryInstance
import io.embrace.opentelemetry.kotlin.context.Context
import io.embrace.opentelemetry.kotlin.createOpenTelemetryKotlin
import io.embrace.opentelemetry.kotlin.creator.current
import io.embrace.opentelemetry.kotlin.k2j.tracing.toOtelKotlinSpanContext
import io.embrace.opentelemetry.kotlin.tracing.model.SpanKind
import io.opentelemetry.semconv.ExceptionAttributes
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalApi::class)
internal class EmbraceSpanImplTest {
    private lateinit var fakeClock: FakeClock
    private lateinit var embraceSpan: EmbraceSdkSpan
    private lateinit var spanRepository: SpanRepository
    private lateinit var serializer: PlatformSerializer
    private lateinit var dataValidator: DataValidator
    private lateinit var embraceSpanFactory: EmbraceSpanFactory
    private var updateNotified: Boolean = false
    private var stoppedSpanId: String? = null
    private val tracer = OpenTelemetryInstance.createOpenTelemetryKotlin().tracerProvider.getTracer(
        "test-tracer"
    )

    @Before
    fun setup() {
        fakeClock = FakeClock()
        val otelClock = FakeOtelKotlinClock(fakeClock)
        spanRepository = SpanRepository().apply { setSpanUpdateNotifier { updateNotified = true } }
        serializer = TestPlatformSerializer()
        dataValidator = DataValidator()
        embraceSpanFactory = EmbraceSpanFactoryImpl(
            openTelemetryClock = otelClock,
            spanRepository = spanRepository,
            dataValidator = dataValidator,
            stopCallback = ::stopCallback,
            redactionFunction = ::redactionFunction
        )
        embraceSpan = embraceSpanFactory.create(
            otelSpanStartArgs = OtelSpanStartArgs(
                name = EXPECTED_SPAN_NAME,
                type = EmbType.Performance.Default,
                internal = false,
                private = false,
                tracer = tracer,
                objectCreator = fakeObjectCreator,
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
            assertEquals(SpanKind.INTERNAL, spanKind)
            assertNull(embraceSpan.snapshot())
        }
        assertFalse(updateNotified)
        assertNull(stoppedSpanId)
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
            assertEquals(EXPECTED_SPAN_NAME, name())
            assertEquals(SpanKind.INTERNAL, spanKind)
            assertEquals(expectedStartTimeMs, getStartTimeMs())
            assertEquals("emb.type", attributes().entries.single().key)
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
        assertTrue(updateNotified)
        assertNull(stoppedSpanId)
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
            assertTrue(addEvent(name = ""))
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
                addEvent(
                    name = "future event",
                    timestampMs = fakeClock.now() + 2L,
                    mapOf(Pair("key", "value"), Pair("key2", "value1"))
                )
            )
            assertTrue(updateNotified)
        }
    }

    @Test
    fun `check adding custom links`() {
        with(embraceSpan) {
            assertTrue(start())
            val linkedSpan = FakeEmbraceSdkSpan.stopped()
            val spanContext = checkNotNull(linkedSpan.spanContext)
            assertTrue(embraceSpan.addLink(spanContext))
            assertTrue(updateNotified)
        }
    }

    @Test
    fun `check adding system links`() {
        with(embraceSpan) {
            assertTrue(start())
            val linkedSpan = FakeEmbraceSdkSpan.stopped()
            val spanContext = checkNotNull(linkedSpan.spanContext)
            assertTrue(embraceSpan.addSystemLink(spanContext.toOtelKotlinSpanContext(), LinkType.PreviousSession))
            assertTrue(updateNotified)
        }
    }

    @Test
    fun `span name update`() {
        with(embraceSpan) {
            assertTrue(embraceSpan.updateName(TOO_LONG_SPAN_NAME))
            assertEquals(TRUNCATED_TOO_LONG_SPAN_NAME, embraceSpan.name())
            assertTrue(embraceSpan.updateName("new-name"))
            assertFalse(embraceSpan.updateName(""))
            assertFalse(embraceSpan.updateName(" "))
            assertTrue(start())
            assertEquals("new-name", embraceSpan.name())
            assertEquals("new-name", embraceSpan.snapshot()?.name)
            assertTrue(embraceSpan.updateName("new-new-name"))
            assertEquals("new-new-name", embraceSpan.name())
            assertEquals("new-new-name", embraceSpan.snapshot()?.name)
            assertTrue(stop())
            assertFalse(embraceSpan.updateName("failed-to-update"))
            assertEquals("new-new-name", embraceSpan.name())
            assertEquals("new-new-name", embraceSpan.snapshot()?.name)
            assertTrue(updateNotified)
        }
    }

    @Test
    fun `recording exceptions as span events`() {
        val timestampNanos = fakeClock.now().millisToNanos()
        val firstException = IllegalStateException("oops")
        val firstExceptionStackTrace = firstException.truncatedStacktraceText()
        val secondException = RuntimeException("haha", firstException)
        val secondExceptionStackTrace = secondException.truncatedStacktraceText()

        with(embraceSpan) {
            assertFalse(recordException(exception = IllegalStateException()))
            assertTrue(start())
            assertTrue(recordException(exception = firstException))
            assertTrue(recordException(exception = secondException, attributes = mapOf("myKey" to "myValue")))
            assertTrue(recordException(exception = RuntimeException(), attributes = tooBigEventAttributes))
            assertTrue(stop())
            assertFalse(recordException(exception = IllegalStateException()))
            assertEquals(3, events().size)
            assertTrue(updateNotified)
        }

        with(checkNotNull(embraceSpan.snapshot())) {
            val sanitizedEvents = checkNotNull(events)
            assertEquals(3, sanitizedEvents.size)
            with(sanitizedEvents[0]) {
                assertEquals(dataValidator.otelLimitsConfig.getExceptionEventName(), name)
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
            with(sanitizedEvents[1]) {
                assertEquals(dataValidator.otelLimitsConfig.getExceptionEventName(), name)
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
            assertTrue(addEvent(name = TOO_LONG_EVENT_NAME))
            assertEquals(TRUNCATED_TOO_LONG_EVENT_NAME, events().last().name)
            assertTrue(addEvent(name = "yo", timestampMs = null, attributes = tooBigEventAttributes))
            assertEquals(10, events().last().attributes?.size)
            assertTrue(addEvent(name = MAX_LENGTH_EVENT_NAME))
            assertTrue(addEvent(name = MAX_LENGTH_EVENT_NAME, timestampMs = null, attributes = null))
            assertTrue(addEvent(name = "yo", timestampMs = null, attributes = maxSizeEventAttributes))
            assertTrue(recordException(exception = RuntimeException()))
            repeat(dataValidator.otelLimitsConfig.getMaxCustomEventCount() - 7) {
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
            val maxCustomAttrCount = dataValidator.otelLimitsConfig.getMaxCustomAttributeCount()
            repeat(maxCustomAttrCount) {
                assertTrue(addAttribute(key = "key$it", value = "value"))
            }
            assertFalse(addAttribute(key = "failed", value = "value"))
            addSystemAttribute("system-attribute", "value")
            assertEquals("value", embraceSpan.snapshot()?.attributes?.findAttributeValue("system-attribute"))
            assertEquals(maxCustomAttrCount + 2, attributes().size)
            removeSystemAttribute("system-attribute")
            assertNull("value", embraceSpan.snapshot()?.attributes?.findAttributeValue("system-attribute"))
            assertEquals(maxCustomAttrCount + 1, attributes().size)
            assertTrue(updateNotified)
        }
    }

    @Test
    fun `check custom attribute limits`() {
        with(embraceSpan) {
            assertTrue(start())
            assertTrue(addAttribute(key = TOO_LONG_ATTRIBUTE_KEY, value = "long-key-value"))
            assertTrue(addAttribute(key = "long-value-key", value = TOO_LONG_ATTRIBUTE_VALUE))
            assertTrue(addAttribute(key = MAX_LENGTH_ATTRIBUTE_KEY, value = "value"))
            assertTrue(addAttribute(key = "key", value = MAX_LENGTH_ATTRIBUTE_VALUE))
            assertTrue(addAttribute(key = "Key", value = MAX_LENGTH_ATTRIBUTE_VALUE))
            repeat(dataValidator.otelLimitsConfig.getMaxCustomAttributeCount() - 5) {
                assertTrue(addAttribute(key = "key$it", value = "value"))
            }
            assertFalse(addAttribute(key = "failedKey", value = "value"))
            assertEquals(dataValidator.otelLimitsConfig.getMaxCustomAttributeCount() + 1, attributes().size)
            val combinedAttributes = attributes()
            with(combinedAttributes) {
                assertEquals(TRUNCATED_TOO_LONG_ATTRIBUTE_VALUE, combinedAttributes["long-value-key"])
                assertEquals("long-key-value", combinedAttributes[TRUNCATED_TOO_LONG_ATTRIBUTE_KEY])
            }

            assertTrue(updateNotified)
        }
    }

    @Test
    fun `check internal span attribute key and value limits`() {
        embraceSpan = createInternalEmbraceSdkSpan()
        with(embraceSpan) {
            assertTrue(start())
            assertTrue(addAttribute(key = TOO_LONG_ATTRIBUTE_KEY_FOR_INTERNAL_SPAN, value = "long-key-value"))
            assertTrue(addAttribute(key = "long-value-key", value = TOO_LONG_ATTRIBUTE_VALUE_FOR_INTERNAL_SPAN))
            assertTrue(addAttribute(key = MAX_LENGTH_ATTRIBUTE_KEY_FOR_INTERNAL_SPAN, value = "value"))
            assertTrue(addAttribute(key = "key", value = MAX_LENGTH_ATTRIBUTE_VALUE_FOR_INTERNAL_SPAN))
            assertTrue(addAttribute(key = "Key", value = MAX_LENGTH_ATTRIBUTE_VALUE_FOR_INTERNAL_SPAN))
            val combinedAttributes = attributes()
            with(combinedAttributes) {
                assertEquals("long-key-value", combinedAttributes[TRUNCATED_TOO_LONG_ATTRIBUTE_KEY_FOR_INTERNAL_SPAN])
                assertEquals(TRUNCATED_TOO_LONG_ATTRIBUTE_VALUE_FOR_INTERNAL_SPAN, combinedAttributes["long-value-key"])
            }
        }
    }

    @Test
    fun `check system link limits`() {
        assertTrue(embraceSpan.start())
        repeat(dataValidator.otelLimitsConfig.getMaxSystemLinkCount()) {
            val spanContext = checkNotNull(FakeEmbraceSdkSpan.stopped().spanContext)
            assertTrue(embraceSpan.addSystemLink(spanContext.toOtelKotlinSpanContext(), LinkType.PreviousSession))
        }

        assertFalse(
            embraceSpan.addSystemLink(
                checkNotNull(FakeEmbraceSdkSpan.stopped().spanContext).toOtelKotlinSpanContext(),
                LinkType.PreviousSession
            )
        )
    }

    @Test
    fun `check custom link limits`() {
        assertTrue(embraceSpan.start())
        repeat(dataValidator.otelLimitsConfig.getMaxCustomLinkCount()) {
            assertTrue(embraceSpan.addLink(FakeEmbraceSdkSpan.stopped()))
        }

        assertFalse(embraceSpan.addLink(FakeEmbraceSdkSpan.stopped()))
    }

    @Test
    fun `validate full snapshot`() {
        embraceSpan = createInternalEmbraceSdkSpan()
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

            val linkedSpan = FakeEmbraceSdkSpan.stopped()
            val linkAttrs = mapOf("link-attr" to "value")
            val spanContext = checkNotNull(linkedSpan.spanContext)
            assertTrue(embraceSpan.addLink(spanContext, linkAttrs))
            assertTrue(embraceSpan.addSystemLink(spanContext.toOtelKotlinSpanContext(), LinkType.PreviousSession))

            val snapshot = checkNotNull(embraceSpan.snapshot())

            assertEquals(traceId, snapshot.traceId)
            assertEquals(spanId, snapshot.spanId)
            assertEquals(OtelIds.invalidSpanId, snapshot.parentSpanId)
            assertEquals(EXPECTED_SPAN_NAME.toEmbraceObjectName(), snapshot.name)
            assertTrue(hasEmbraceAttribute(EmbType.System.LowPower))
            assertTrue(hasEmbraceAttribute(PrivateSpan))
            assertEquals(expectedStartTimeMs.millisToNanos(), snapshot.startTimeNanos)
            assertEquals(Span.Status.UNSET, snapshot.status)

            val snapshotEvent = checkNotNull(snapshot.events).single()
            assertEquals(EXPECTED_EVENT_NAME, snapshotEvent.name)
            assertEquals(expectedEventTime.millisToNanos(), snapshotEvent.timestampNanos)

            val eventAttributes =
                checkNotNull(snapshotEvent.attributes).single { !checkNotNull(it.key).startsWith("emb.") }
            assertEquals(EXPECTED_ATTRIBUTE_NAME, eventAttributes.key)
            assertEquals(EXPECTED_ATTRIBUTE_VALUE, eventAttributes.data)

            val snapshotAttributes =
                checkNotNull(snapshot.attributes).single { !checkNotNull(it.key).startsWith("emb.") }
            assertEquals(EXPECTED_ATTRIBUTE_NAME, snapshotAttributes.key)
            assertEquals(EXPECTED_ATTRIBUTE_VALUE, snapshotAttributes.data)

            // TODO: fix links to be returned in insertion order
            val snapshotLinks = checkNotNull(snapshot.links)
            snapshotLinks[1].validateLinkToSpan(checkNotNull(value = linkedSpan.snapshot()), expectedAttributes = linkAttrs)
            snapshotLinks[0].validateSystemLink(checkNotNull(linkedSpan.snapshot()), LinkType.PreviousSession)
        }
    }

    @Test
    fun `start time from span start method overrides all`() {
        val wrapper = createWrapperForInternalSpan(startTimeMs = fakeClock.tick())
        embraceSpan = embraceSpanFactory.create(wrapper)

        val timePassedIn = fakeClock.tick()
        fakeClock.tick()
        assertTrue(embraceSpan.start(startTimeMs = timePassedIn))
        assertEquals(timePassedIn, embraceSpan.snapshot()?.startTimeNanos?.nanosToMillis())
    }

    @Test
    fun `OTel clock used if start time passed is zero`() {
        assertTrue(embraceSpan.start(startTimeMs = 0L))
        assertEquals(fakeClock.now().millisToNanos(), embraceSpan.snapshot()?.startTimeNanos)
    }

    @Test
    fun `start time from span builder used if no start time passed into start method`() {
        val timeOnWrapper = fakeClock.tick()
        val wrapper = createWrapperForInternalSpan(startTimeMs = timeOnWrapper)
        embraceSpan = embraceSpanFactory.create(wrapper)
        fakeClock.tick()
        assertTrue(embraceSpan.start())
        assertEquals(timeOnWrapper, embraceSpan.snapshot()?.startTimeNanos?.nanosToMillis())
    }

    @Test
    fun `validate context objects are propagated from the parent to the child span`() {
        val newParentContext = fakeObjectCreator.context.current().set(fakeContextKey, "fake-value")
        val wrapper = createWrapperForInternalSpan(parentContext = newParentContext)
        embraceSpan = embraceSpanFactory.create(wrapper)

        assertNull(embraceSpan.asNewContext())
        assertTrue(embraceSpan.start())
        val newSpanContext = checkNotNull(embraceSpan.asNewContext())
        assertEquals("fake-value", newSpanContext.get(fakeContextKey))
    }

    @Test
    fun `custom attributes are redacted if their key is sensitive when getting a span snapshot`() {
        // given a span with a sensitive key
        val spanBuilder = createWrapperForInternalSpan()
        spanBuilder.customAttributes["password"] = "123456"
        spanBuilder.customAttributes["status"] = "ok"
        embraceSpan = embraceSpanFactory.create(spanBuilder)
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
        embraceSpan = createInternalEmbraceSdkSpan()
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

    private fun createInternalEmbraceSdkSpan() = embraceSpanFactory.create(createWrapperForInternalSpan())

    private fun createWrapperForInternalSpan(startTimeMs: Long? = null, parentContext: Context? = null) = OtelSpanStartArgs(
        name = EXPECTED_SPAN_NAME,
        type = EmbType.System.LowPower,
        internal = true,
        private = true,
        tracer = tracer,
        parentCtx = parentContext,
        startTimeMs = startTimeMs,
        objectCreator = fakeObjectCreator,
    )

    private fun EmbraceSdkSpan.assertSnapshot(
        expectedStartTimeMs: Long,
        expectedEndTimeMs: Long? = null,
        expectedType: EmbType = EmbType.Performance.Default,
        expectedStatus: Span.Status = Span.Status.UNSET,
        eventCount: Int = 0,
        expectedEmbraceAttributes: Int = 1,
        expectedCustomAttributeCount: Int = 0,
        isPrivate: Boolean = false,
    ) {
        with(checkNotNull(snapshot())) {
            assertEquals(expectedStartTimeMs, startTimeNanos?.nanosToMillis())
            assertEquals(expectedEndTimeMs, endTimeNanos?.nanosToMillis())
            assertEquals(expectedStatus, status)
            assertEquals(eventCount, events?.size)
            assertTrue(hasEmbraceAttribute(expectedType))
            assertEquals(isPrivate, hasEmbraceAttribute(PrivateSpan))
            val attrs = checkNotNull(attributes)
            val embraceAttributeCount = attrs.filter { checkNotNull(it.key).startsWith("emb.") }.size
            val customAttributeCount = attrs.size - embraceAttributeCount
            assertEquals(expectedEmbraceAttributes, embraceAttributeCount)
            assertEquals(expectedCustomAttributeCount, customAttributeCount)
        }
    }

    private fun EmbraceSdkSpan.validateStoppedSpan() {
        assertFalse(stop())
        assertNotNull(traceId)
        assertNotNull(spanId)
        assertFalse(isRecording)
        assertFalse(addEvent("eventName"))
        assertFalse(addAttribute("first", "value"))
        assertEquals(0, spanRepository.getActiveSpans().size)
        assertEquals(1, spanRepository.getCompletedSpans().size)
        assertTrue(updateNotified)
        assertEquals(stoppedSpanId, spanId)
    }

    private fun redactionFunction(key: String, value: String): String {
        return if (key == "password") {
            REDACTED_LABEL
        } else {
            value
        }
    }

    private fun stopCallback(spanId: String) {
        stoppedSpanId = spanId
    }

    companion object {
        private const val EXPECTED_SPAN_NAME = "test-span"
        private const val EXPECTED_EVENT_NAME = "fun event 🔥"
        private const val EXPECTED_ATTRIBUTE_NAME = "attribute-key"
        private const val EXPECTED_ATTRIBUTE_VALUE = "harharhar"
        private const val REDACTED_LABEL = "<redacted>"
    }
}

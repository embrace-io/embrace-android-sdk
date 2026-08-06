package io.embrace.android.embracesdk.internal.otel.spans

import androidx.test.ext.junit.runners.AndroidJUnit4
import io.embrace.android.embracesdk.assertions.assertError
import io.embrace.android.embracesdk.assertions.assertIsPrivateSpan
import io.embrace.android.embracesdk.assertions.assertIsType
import io.embrace.android.embracesdk.assertions.assertIsTypePerformance
import io.embrace.android.embracesdk.assertions.assertNotPrivateSpan
import io.embrace.android.embracesdk.fakes.FakeClock
import io.embrace.android.embracesdk.fakes.FakeOtelKotlinClock
import io.embrace.android.embracesdk.fakes.FakeSessionIdsProvider
import io.embrace.android.embracesdk.fakes.FakeSpanService
import io.embrace.android.embracesdk.fakes.FakeTelemetryService
import io.embrace.android.embracesdk.fakes.TestConstants.TESTS_DEFAULT_USE_KOTLIN_SDK
import io.embrace.android.embracesdk.fakes.TestUuidSource
import io.embrace.android.embracesdk.fixtures.MAX_LENGTH_INTERNAL_SPAN_NAME
import io.embrace.android.embracesdk.fixtures.MAX_LENGTH_SPAN_NAME
import io.embrace.android.embracesdk.fixtures.TOO_LONG_ATTRIBUTE_KEY
import io.embrace.android.embracesdk.fixtures.TOO_LONG_ATTRIBUTE_VALUE
import io.embrace.android.embracesdk.fixtures.TOO_LONG_INTERNAL_SPAN_NAME
import io.embrace.android.embracesdk.fixtures.TOO_LONG_SPAN_NAME
import io.embrace.android.embracesdk.fixtures.maxSizeCustomAttributes
import io.embrace.android.embracesdk.fixtures.maxSizeCustomEvents
import io.embrace.android.embracesdk.fixtures.maxSizeSystemAttributes
import io.embrace.android.embracesdk.fixtures.maxSizeSystemEvents
import io.embrace.android.embracesdk.fixtures.tooBigCustomAttributes
import io.embrace.android.embracesdk.fixtures.tooBigCustomEvents
import io.embrace.android.embracesdk.fixtures.tooBigSystemAttributes
import io.embrace.android.embracesdk.fixtures.tooBigSystemEvents
import io.embrace.android.embracesdk.internal.SystemInfo
import io.embrace.android.embracesdk.internal.arch.datasource.SpanEvent
import io.embrace.android.embracesdk.internal.arch.datasource.SpanEventImpl
import io.embrace.android.embracesdk.internal.arch.schema.EmbType
import io.embrace.android.embracesdk.internal.clock.millisToNanos
import io.embrace.android.embracesdk.internal.clock.nanosToMillis
import io.embrace.android.embracesdk.internal.otel.config.OtelSdkConfig
import io.embrace.android.embracesdk.internal.otel.logs.LogSinkImpl
import io.embrace.android.embracesdk.internal.otel.payload.toEmbracePayload
import io.embrace.android.embracesdk.internal.otel.sdk.DataValidator
import io.embrace.android.embracesdk.internal.otel.sdk.OtelSdkWrapper
import io.embrace.android.embracesdk.internal.otel.sdk.findAttributeValue
import io.embrace.android.embracesdk.internal.otel.sdk.id.OtelIds
import io.embrace.android.embracesdk.internal.payload.Span
import io.embrace.android.embracesdk.spans.EmbraceSpan
import io.embrace.android.embracesdk.spans.EmbraceSpanEvent
import io.embrace.android.embracesdk.spans.ErrorCode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

@RunWith(AndroidJUnit4::class)
internal class SpanServiceImplTest {
    private lateinit var spanRepository: SpanRepository
    private lateinit var dataValidator: DataValidator
    private lateinit var spansService: SpanServiceImpl
    private val clock = FakeClock()
    private val otelClock = FakeOtelKotlinClock(clock)
    private var spanCreationAllowed: Boolean = true
    private var initTimeMs: Long = 0L
    private var initCallbackCount: Int = 0
    private var failNextTracerLookup: Boolean = false

    @Before
    fun setup() {
        spanRepository = SpanRepository()
        dataValidator = DataValidator(telemetryService = FakeTelemetryService())
        spansService = createSpanService(dataValidator)
    }

    @Test
    fun `create trace with default parameters`() {
        val embraceSpan = checkNotNull(spansService.createSpan("test-span"))
        assertNull(embraceSpan.parent)
        assertTrue(embraceSpan.start())
        assertTrue(embraceSpan.stop())
        with(verifyAndReturnSoleCompletedSpan("emb-test-span")) {
            assertEquals(OtelIds.INVALID_SPAN_ID, parentSpanId)
            assertIsTypePerformance()
            assertNotPrivateSpan()
        }
    }

    @Test
    fun `create trace that is internally logged but public`() {
        val embraceSpan = checkNotNull(
            spansService.createSpan(name = "test-span", internal = true, private = false),
        )
        assertNull(embraceSpan.parent)
        assertTrue(embraceSpan.start())
        assertTrue(embraceSpan.stop())
        with(verifyAndReturnSoleCompletedSpan("emb-test-span")) {
            assertNotPrivateSpan()
        }
    }

    @Test
    fun `create trace that is private but not considered internally logged`() {
        val embraceSpan = checkNotNull(
            spansService.createSpan(name = "test-span", internal = false, private = true),
        )
        assertNull(embraceSpan.parent)
        assertTrue(embraceSpan.start())
        assertTrue(embraceSpan.stop())
        with(verifyAndReturnSoleCompletedSpan("test-span")) {
            assertIsPrivateSpan()
        }
    }

    @Test
    fun `create trace with custom start and end times`() {
        val embraceSpan = checkNotNull(spansService.createSpan(name = "test-span"))
        assertNull(embraceSpan.parent)
        assertTrue(embraceSpan.start(clock.now() - 1))
        assertTrue(embraceSpan.stop(endTimeMs = clock.now() + 10))
        verifyAndReturnSoleCompletedSpan("emb-test-span")
    }

    @Test
    fun `create trace with custom type`() {
        val embraceSpan = checkNotNull(
            spansService.createSpan(
                name = "test-span",
                type = EmbType.Performance.Default,
            ),
        )
        assertTrue(embraceSpan.start())
        assertTrue(embraceSpan.stop())
        with(verifyAndReturnSoleCompletedSpan("emb-test-span")) {
            assertEquals(OtelIds.INVALID_SPAN_ID, parentSpanId)
            assertIsTypePerformance()
        }
    }

    @Test
    fun `create trace with children`() {
        val parentSpan = spansService.createSpan(name = "test-span")
        checkNotNull(parentSpan).start()
        val childSpan = spansService.createSpan(name = "child-span", parent = parentSpan)
        checkNotNull(childSpan).start()
        assertTrue(parentSpan.traceId == childSpan.traceId)
        assertTrue(parentSpan.spanId == checkNotNull(childSpan.parent).spanId)
        assertTrue(childSpan.stop())
        assertTrue(parentSpan.stop())

        val currentSpans = spanRepository.completedOtelSpans()
        assertEquals(2, currentSpans.size)
        assertTrue(currentSpans[0].traceId == currentSpans[1].traceId)

        with(currentSpans[0]) {
            assertEquals("emb-child-span", name)
            assertEquals(childSpan.spanId, spanId)
            assertEquals(childSpan.traceId, traceId)
            assertNotPrivateSpan()
        }

        with(currentSpans[1]) {
            assertEquals("emb-test-span", name)
            assertEquals(OtelIds.INVALID_SPAN_ID, parentSpanId)
            assertEquals(parentSpan.spanId, spanId)
            assertEquals(parentSpan.traceId, traceId)
            assertNotPrivateSpan()
        }
    }

    @Test
    fun `cannot create span with if validation fails`() {
        spanCreationAllowed = false
        assertEquals(NoopEmbraceSdkSpan, spansService.createSpan(name = "test"))
    }

    @Test
    fun `cannot create span with blank name`() {
        assertEquals(NoopEmbraceSdkSpan, spansService.createSpan(name = ""))
        assertEquals(NoopEmbraceSdkSpan, spansService.createSpan(name = " "))
    }

    @Test
    fun `start a span directly`() {
        spanRepository.flushOtelSpans()
        val parentStartTime = clock.now()
        val parent = checkNotNull(spansService.startSpan(name = "test-span", private = false))
        val childStartTimeMs = clock.now() + 10L
        val child = checkNotNull(
            spansService.startSpan(
                name = "child-span",
                parent = parent,
                startTimeMs = childStartTimeMs,
                type = EmbType.Ux.View,
            ),
        )
        clock.tick(40L)
        val childSpanEndTimeMs = clock.now()
        assertTrue(child.stop())
        with(spanRepository.flushOtelSpans().single()) {
            assertEquals("emb-child-span", name)
            assertEquals(childStartTimeMs, startTimeNanos?.nanosToMillis())
            assertEquals(childSpanEndTimeMs, endTimeNanos?.nanosToMillis())
            assertNotPrivateSpan()
            assertIsType(EmbType.Ux.View)
        }
        clock.tick(10)
        val parentEndTime = clock.now()
        assertTrue(parent.stop())
        with(spanRepository.flushOtelSpans().single()) {
            assertEquals("emb-test-span", name)
            assertEquals(parentStartTime, startTimeNanos?.nanosToMillis())
            assertEquals(parentEndTime, endTimeNanos?.nanosToMillis())
            assertNotPrivateSpan()
        }
    }

    @Test
    fun `record internal but public completed span with all the fixings`() {
        val expectedName = "test-span"
        val expectedStartTimeMs = clock.now()
        val expectedEndTimeMs = expectedStartTimeMs + 100L
        val expectedType = EmbType.Performance.Default
        val expectedAttributes = mapOf(
            Pair("attribute1", "value1"),
            Pair("attribute2", "value2"),
        )
        val expectedEvents = listOfNotNull(
            EmbraceSpanEvent.create(name = "event1", timestampMs = 1_000_000L.nanosToMillis(), expectedAttributes),
            EmbraceSpanEvent.create(name = "event2", timestampMs = 5_000_000L.nanosToMillis(), expectedAttributes),
        )

        spansService.recordCompletedSpan(
            name = expectedName,
            startTimeMs = expectedStartTimeMs,
            endTimeMs = expectedEndTimeMs,
            type = expectedType,
            private = false,
            attributes = expectedAttributes,
            events = expectedEvents.map { it.toSpanEvent() },
        )

        with(verifyAndReturnSoleCompletedSpan("emb-$expectedName")) {
            assertEquals(expectedStartTimeMs, startTimeNanos?.nanosToMillis())
            assertEquals(expectedEndTimeMs, endTimeNanos?.nanosToMillis())
            assertIsTypePerformance()
            assertEquals(OtelIds.INVALID_SPAN_ID, parentSpanId)
            assertNotPrivateSpan()
            expectedAttributes.forEach {
                assertEquals(it.value, attributes?.findAttributeValue(it.key))
            }
            assertEquals(expectedEvents.map(EmbraceSpanEvent::toEmbracePayload), events)
        }
    }

    @Test
    fun `record completed child span`() {
        val expectedName = "child-span"
        val expectedStartTimeMs = clock.now()
        val expectedEndTimeMs = expectedStartTimeMs + 100L
        val parentSpan = checkNotNull(spansService.createSpan(name = "test-span"))
        assertTrue(parentSpan.start())
        assertTrue(
            spansService.recordCompletedSpan(
                name = expectedName,
                startTimeMs = expectedStartTimeMs,
                endTimeMs = expectedEndTimeMs,
                parent = parentSpan,
            ),
        )

        with(verifyAndReturnSoleCompletedSpan("emb-$expectedName")) {
            assertEquals(expectedStartTimeMs, startTimeNanos?.nanosToMillis())
            assertEquals(expectedEndTimeMs, endTimeNanos?.nanosToMillis())
            assertNotPrivateSpan()
        }
        assertTrue(parentSpan.stop())

        val currentSpans = spanRepository.completedOtelSpans()
        assertEquals(2, currentSpans.size)
        assertTrue(currentSpans[0].traceId == currentSpans[1].traceId)
        assertTrue(currentSpans[0].parentSpanId == currentSpans[1].spanId)
    }

    @Test
    fun `record spans with different ending error codes `() {
        ErrorCode.entries.forEach { errorCode ->
            assertTrue(
                spansService.recordCompletedSpan(
                    name = "test${errorCode.name}",
                    startTimeMs = 0,
                    endTimeMs = 1,
                    errorCode = errorCode.toErrorCodeAttribute(),
                ),
            )
            with(verifyAndReturnSoleCompletedSpan("emb-test${errorCode.name}")) {
                assertError(errorCode)
            }
            spanRepository.flushOtelSpans()
        }
    }

    @Test
    fun `validate start and end times for a completed span`() {
        assertFalse(
            spansService.recordCompletedSpan(
                name = "test-pan",
                startTimeMs = 500,
                endTimeMs = 499,
            ),
        )
    }

    @Test
    fun `nanosecond timestamps for a completed span are normalized to millis`() {
        val expectedStartTimeMs = clock.now()
        val expectedEndTimeMs = expectedStartTimeMs + 100L

        assertTrue(
            spansService.recordCompletedSpan(
                name = "test-span",
                startTimeMs = expectedStartTimeMs.millisToNanos(),
                endTimeMs = expectedEndTimeMs.millisToNanos(),
            ),
        )

        with(verifyAndReturnSoleCompletedSpan("emb-test-span")) {
            assertEquals(expectedStartTimeMs, startTimeNanos?.nanosToMillis())
            assertEquals(expectedEndTimeMs, endTimeNanos?.nanosToMillis())
        }
    }

    @Test
    fun `completed span timestamps are normalized before start and end times are validated`() {
        val expectedStartTimeMs = clock.now()
        val expectedEndTimeMs = expectedStartTimeMs + 100L

        // mixed units: the raw values compare as start > end, but the normalized ones do not
        assertTrue(
            spansService.recordCompletedSpan(
                name = "test-span",
                startTimeMs = expectedStartTimeMs.millisToNanos(),
                endTimeMs = expectedEndTimeMs,
            ),
        )

        with(verifyAndReturnSoleCompletedSpan("emb-test-span")) {
            assertEquals(expectedStartTimeMs, startTimeNanos?.nanosToMillis())
            assertEquals(expectedEndTimeMs, endTimeNanos?.nanosToMillis())
        }
    }

    @Test
    fun `validate normalized start and end times for a completed span`() {
        assertFalse(
            spansService.recordCompletedSpan(
                name = "test-span",
                startTimeMs = (clock.now() + 100L).millisToNanos(),
                endTimeMs = clock.now().millisToNanos(),
            ),
        )
    }

    @Test
    fun `record lambda running as an internal but public trace`() {
        val returnThis = "yooooo"
        val lambdaReturn = spansService.recordSpan(name = "test-span", private = false) {
            returnThis
        }

        assertEquals(returnThis, lambdaReturn)
        with(verifyAndReturnSoleCompletedSpan("emb-test-span")) {
            assertEquals(OtelIds.INVALID_SPAN_ID, parentSpanId)
            assertIsTypePerformance()
            assertNotPrivateSpan()
        }
    }

    @Test
    fun `record lambda running as a child span`() {
        val parentSpan = checkNotNull(spansService.createSpan(name = "test-span"))
        assertTrue(parentSpan.start())
        spansService.recordSpan(name = "child-span", parent = parentSpan) {
            parentSpan.hashCode()
        }

        assertTrue(parentSpan.stop())

        val currentSpans = spanRepository.completedOtelSpans()
        assertEquals(2, currentSpans.size)
        assertTrue(currentSpans[0].traceId == currentSpans[1].traceId)
        assertTrue(currentSpans[0].parentSpanId == currentSpans[1].spanId)

        with(currentSpans[0]) {
            assertEquals("emb-child-span", name)
            assertNotPrivateSpan()
        }
    }

    @Test
    fun `recording span as lambda throws an exception will record a failed span and rethrows exception`() {
        assertThrows(RuntimeException::class.java) {
            spansService.recordSpan(name = "test-span") {
                throw RuntimeException("You done bad")
            }
        }

        with(verifyAndReturnSoleCompletedSpan("emb-test-span")) {
            assertError(ErrorCode.FAILURE)
        }
    }

    @Test
    fun `recording span as lambda when span cannot be recorded will run code but not log span`() {
        spanCreationAllowed = false
        var executed = false
        spansService.recordSpan(name = "test-span") {
            executed = true
        }

        assertTrue(executed)
        assertEquals(0, spanRepository.completedOtelSpans().size)
    }

    @Test
    fun `check name length limit for non-internal spans`() {
        assertNotNull(spansService.createSpan(name = TOO_LONG_SPAN_NAME, internal = false))
        assertTrue(
            spansService.recordCompletedSpan(
                name = TOO_LONG_SPAN_NAME,
                startTimeMs = 100L,
                endTimeMs = 200L,
                internal = false,
            ),
        )
        assertNotNull(spansService.recordSpan(name = TOO_LONG_SPAN_NAME, internal = false) { 1 })
        assertEquals(2, spanRepository.completedOtelSpans().size)
        assertNotNull(spansService.createSpan(name = MAX_LENGTH_SPAN_NAME, internal = false))
        assertNotNull(spansService.recordSpan(name = MAX_LENGTH_SPAN_NAME, internal = false) { 2 })
        assertTrue(
            spansService.recordCompletedSpan(
                name = MAX_LENGTH_SPAN_NAME,
                startTimeMs = 100L,
                endTimeMs = 200L,
                internal = false,
            ),
        )
        assertEquals(4, spanRepository.completedOtelSpans().size)
    }

    @Test
    fun `check limits for internal spans`() {
        assertNotNull(spansService.createSpan(name = TOO_LONG_INTERNAL_SPAN_NAME, internal = true))
        assertTrue(
            spansService.recordCompletedSpan(
                name = TOO_LONG_INTERNAL_SPAN_NAME,
                startTimeMs = 100L,
                endTimeMs = 200L,
                internal = true,
            ),
        )
        assertTrue(
            spansService.recordCompletedSpan(
                name = MAX_LENGTH_INTERNAL_SPAN_NAME,
                startTimeMs = 100L,
                endTimeMs = 200L,
                internal = true,
                attributes = tooBigSystemAttributes,
            ),
        )
        assertTrue(
            spansService.recordCompletedSpan(
                name = MAX_LENGTH_INTERNAL_SPAN_NAME,
                startTimeMs = 100L,
                endTimeMs = 200L,
                internal = true,
                events = tooBigSystemEvents,
            ),
        )
        assertNotNull(
            spansService.recordSpan(name = TOO_LONG_INTERNAL_SPAN_NAME, internal = true) {
                1
            },
        )
        assertNotNull(spansService.createSpan(name = MAX_LENGTH_INTERNAL_SPAN_NAME, internal = true))
        assertNotNull(
            spansService.recordSpan(name = MAX_LENGTH_INTERNAL_SPAN_NAME, internal = true) {
                2
            },
        )
        assertTrue(
            spansService.recordCompletedSpan(
                name = MAX_LENGTH_INTERNAL_SPAN_NAME,
                startTimeMs = 100L,
                endTimeMs = 200L,
                internal = true,
                attributes = maxSizeSystemAttributes,
                events = maxSizeSystemEvents,
            ),
        )
        val completedSpans = spanRepository.completedOtelSpans()
        assertEquals(6, completedSpans.size)
        assertEquals(2, completedSpans.filter { it.name?.endsWith("...") == true }.size)
    }

    @Test
    fun `check events limit`() {
        val maxEventAttrCount = dataValidator.otelLimitsConfig.getMaxEventAttributeCount()
        assertTrue(
            spansService.recordCompletedSpan(
                name = "too many events",
                startTimeMs = 100L,
                endTimeMs = 200L,
                internal = false,
                events = tooBigCustomEvents,
            ),
        )
        assertTrue(
            spansService.recordCompletedSpan(
                name = MAX_LENGTH_SPAN_NAME,
                startTimeMs = 100L,
                endTimeMs = 200L,
                internal = false,
                events = maxSizeCustomEvents,
            ),
        )

        assertEquals(maxEventAttrCount, spanRepository.flushOtelSpans().single { it.name == "too many events" }.events?.size)

        val attributesMap = mutableMapOf(
            Pair(TOO_LONG_ATTRIBUTE_KEY, "value"),
            Pair("key", TOO_LONG_ATTRIBUTE_VALUE),
        )
        repeat(8) {
            attributesMap["key$it"] = "value"
        }

        val events = mutableListOf<SpanEvent>(SpanEventImpl("event", 100L, attributesMap))
        repeat(dataValidator.otelLimitsConfig.getMaxCustomEventCount() - 1) {
            events.add(SpanEventImpl("event", 100L, emptyMap()))
        }
        assertTrue(
            spansService.recordCompletedSpan(
                name = MAX_LENGTH_SPAN_NAME,
                startTimeMs = 100L,
                endTimeMs = 200L,
                internal = false,
                events = events,
            ),
        )

        val completedSpans = spanRepository.completedOtelSpans()
        assertEquals(1, completedSpans.size)
        assertEquals(10, completedSpans[0].events?.size)
        assertEquals(maxEventAttrCount, completedSpans[0].events?.get(0)?.attributes?.size)
    }

    @Test
    fun `check attributes limit`() {
        assertTrue(
            spansService.recordCompletedSpan(
                name = "too many attributes",
                startTimeMs = 100L,
                endTimeMs = 200L,
                internal = false,
                attributes = tooBigCustomAttributes,
            ),
        )
        assertTrue(
            spansService.recordCompletedSpan(
                name = MAX_LENGTH_SPAN_NAME,
                startTimeMs = 100L,
                endTimeMs = 200L,
                internal = false,
                attributes = maxSizeCustomAttributes,
            ),
        )

        spanRepository.flushOtelSpans()

        val maxCustomAttrCount = dataValidator.otelLimitsConfig.getMaxCustomAttributeCount()
        val attributesMap = mutableMapOf(
            Pair(TOO_LONG_ATTRIBUTE_KEY, "value"),
            Pair("ssskey", TOO_LONG_ATTRIBUTE_VALUE),
        )
        repeat(maxCustomAttrCount) {
            attributesMap["test-key$it"] = "value"
        }

        assertTrue(
            spansService.recordCompletedSpan(
                name = MAX_LENGTH_SPAN_NAME,
                startTimeMs = 100L,
                endTimeMs = 200L,
                internal = false,
                attributes = attributesMap,
            ),
        )

        val truncatedAttributesSpan = spanRepository.completedOtelSpans().single { it.name == MAX_LENGTH_SPAN_NAME }
        val attrs = checkNotNull(truncatedAttributesSpan.attributes)
        val truncatedAttrCount = attrs.filter { it.key?.startsWith("sss") == true }.size
        assertEquals(2, truncatedAttrCount)
        assertEquals(98, attrs.filter { it.key?.startsWith("test-key") == true }.size)
    }

    @Test
    fun `bypass validation for non-internal spans`() {
        spansService = createSpanService(DataValidator(bypassValidation = { true }, telemetryService = FakeTelemetryService()))

        assertNotNull(spansService.createSpan(name = TOO_LONG_SPAN_NAME, internal = false))
        assertTrue(
            spansService.recordCompletedSpan(
                name = TOO_LONG_SPAN_NAME,
                startTimeMs = 100L,
                endTimeMs = 200L,
                internal = false,
            ),
        )
        assertTrue(
            spansService.recordCompletedSpan(
                name = "too many events",
                startTimeMs = 100L,
                endTimeMs = 200L,
                internal = false,
                events = tooBigCustomEvents,
            ),
        )
        assertTrue(
            spansService.recordCompletedSpan(
                name = "too many attributes",
                startTimeMs = 100L,
                endTimeMs = 200L,
                internal = false,
                attributes = tooBigCustomAttributes,
            ),
        )
    }

    @Test
    fun `validation for internal spans still enforced even when non-internal limits bypassed`() {
        spansService = createSpanService(DataValidator(bypassValidation = { true }, telemetryService = FakeTelemetryService()))

        assertNotNull(spansService.createSpan(name = TOO_LONG_INTERNAL_SPAN_NAME, internal = true))
        assertTrue(
            spansService.recordCompletedSpan(
                name = TOO_LONG_INTERNAL_SPAN_NAME,
                startTimeMs = 100L,
                endTimeMs = 200L,
                internal = true,
            ),
        )
        assertTrue(
            spansService.recordCompletedSpan(
                name = "too many events",
                startTimeMs = 100L,
                endTimeMs = 200L,
                internal = true,
                events = tooBigSystemEvents,
            ),
        )
        assertTrue(
            spansService.recordCompletedSpan(
                name = "too many attributes",
                startTimeMs = 100L,
                endTimeMs = 200L,
                internal = true,
                attributes = tooBigSystemAttributes,
            ),
        )

        assertEquals(3, spanRepository.completedOtelSpans().size)
    }

    @Test
    fun `verify default behaviour before initialization`() {
        val uninitializedService = createUninitializedSpanService()
        assertFalse(uninitializedService.initialized())
        assertEquals(NoopEmbraceSdkSpan, uninitializedService.createSpan("test-span"))
        assertEquals(NoopEmbraceSdkSpan, uninitializedService.startSpan("test-span"))
        assertTrue(uninitializedService.recordCompletedSpan("test-span", 10, 20))
        var lambdaRan = false
        uninitializedService.recordSpan("test-span") { lambdaRan = true }
        assertTrue(lambdaRan)
        assertEquals(0, spanRepository.completedOtelSpans().size)
    }

    @Test
    fun `service works once initialized`() {
        assertTrue(spansService.initialized())
        assertNotEquals(NoopEmbraceSdkSpan, spansService.createSpan("test-span"))
        assertNotEquals(NoopEmbraceSdkSpan, spansService.startSpan("test-span"))
        assertTrue(spansService.recordCompletedSpan("test-span", 10, 20))
        var lambdaRan = false
        spansService.recordSpan("test-span") { lambdaRan = true }
        assertTrue(lambdaRan)
        assertEquals(2, spanRepository.completedOtelSpans().size)
    }

    @Test
    fun `getSpan returns null before initialization`() {
        assertNull(createUninitializedSpanService().getSpan("some-span-id"))
    }

    @Test
    fun `completed spans recorded before initialization are buffered and replayed`() {
        val service = createUninitializedSpanService()
        assertTrue(service.recordCompletedSpan("test-span", 10, 20))
        assertTrue(service.recordCompletedSpan("other-span", 15, 25))
        assertEquals(0, spanRepository.completedOtelSpans().size)

        service.initializeService(otelClock.now().nanosToMillis())

        assertEquals(
            setOf("emb-other-span", "emb-test-span"),
            spanRepository.completedOtelSpans().map { it.name }.toSet(),
        )
    }

    @Test
    fun `nanosecond timestamps buffered before initialization are normalized on replay`() {
        val service = createUninitializedSpanService()
        val expectedStartTimeMs = clock.now()
        val expectedEndTimeMs = expectedStartTimeMs + 100L
        assertTrue(
            service.recordCompletedSpan(
                name = "test-span",
                startTimeMs = expectedStartTimeMs.millisToNanos(),
                endTimeMs = expectedEndTimeMs.millisToNanos(),
            ),
        )

        service.initializeService(otelClock.now().nanosToMillis())

        with(verifyAndReturnSoleCompletedSpan("emb-test-span")) {
            assertEquals(expectedStartTimeMs, startTimeNanos?.nanosToMillis())
            assertEquals(expectedEndTimeMs, endTimeNanos?.nanosToMillis())
        }
    }

    @Test
    fun `completed spans recorded after initialization are recorded directly`() {
        val service = createUninitializedSpanService()
        service.initializeService(otelClock.now().nanosToMillis())

        assertTrue(service.recordCompletedSpan("after-init", 10, 20))
        assertEquals("emb-after-init", spanRepository.completedOtelSpans().single().name)
    }

    @Test
    fun `completed span recorded while buffered spans are replayed is not lost`() {
        val replayStarted = CountDownLatch(1)
        val service = createUninitializedSpanService(
            spanFactoryDecorator = { SlowEmbraceSpanFactory(it, replayStarted) },
        )
        repeat(5) { i ->
            assertTrue(service.recordCompletedSpan("pre-init-$i", 10, 20))
        }

        val initializer = Thread { service.initializeService(otelClock.now().nanosToMillis()) }
        val recorder = Thread {
            assertTrue(replayStarted.await(2, TimeUnit.SECONDS))
            service.recordCompletedSpan("racing-span", 10, 20)
        }
        initializer.start()
        recorder.start()
        initializer.join(SERVICE_INIT_TIMEOUT_MS)
        recorder.join(SERVICE_INIT_TIMEOUT_MS)

        // 5 buffered spans plus the one that raced with the replay: whichever side of the handover it landed on, it must be recorded
        val completedSpans = spanRepository.completedOtelSpans()
        assertEquals(6, completedSpans.size)
        assertTrue(completedSpans.any { it.name == "emb-racing-span" })
    }

    @Test
    fun `initializeService is idempotent`() {
        val service = createUninitializedSpanService()
        assertTrue(service.recordCompletedSpan("test-span", 10, 20))
        initCallbackCount = 0

        service.initializeService(100L)
        service.initializeService(200L)

        assertEquals(1, initCallbackCount)
        assertEquals(100L, initTimeMs)
        assertEquals(1, spanRepository.completedOtelSpans().size)
    }

    @Test
    fun `buffered spans survive a failed initializeService`() {
        val service = createUninitializedSpanService()
        assertTrue(service.recordCompletedSpan("test-span", 10, 20))
        initCallbackCount = 0

        failNextTracerLookup = true
        assertThrows(IllegalStateException::class.java) {
            service.initializeService(otelClock.now().nanosToMillis())
        }
        assertFalse(service.initialized())
        assertEquals(0, initCallbackCount)

        service.initializeService(otelClock.now().nanosToMillis())

        assertTrue(service.initialized())
        assertEquals("emb-test-span", spanRepository.completedOtelSpans().single().name)
    }

    @Test
    fun `recordCompletedSpan with invalid times is buffered before initialization then dropped on replay`() {
        val service = createUninitializedSpanService()
        assertTrue(service.recordCompletedSpan(name = "test-span", startTimeMs = 500, endTimeMs = 499))

        service.initializeService(otelClock.now().nanosToMillis())

        assertEquals(0, spanRepository.completedOtelSpans().size)
    }

    @Test
    fun `verify ceiling to how many recordCompleteSpan calls can be buffered`() {
        val service = createUninitializedSpanService()
        repeat(MAX_BUFFERED_CALLS) {
            assertTrue(service.recordCompletedSpan("test-span", 10, 20))
        }
        assertFalse(service.recordCompletedSpan("test-span", 10, 20))
    }

    private fun createSpanService(
        dataValidator: DataValidator = this.dataValidator,
        spanFactoryDecorator: (EmbraceSpanFactory) -> EmbraceSpanFactory = { it },
    ): SpanServiceImpl = createUninitializedSpanService(dataValidator, spanFactoryDecorator).apply {
        initializeService(otelClock.now().nanosToMillis())
    }

    private fun createUninitializedSpanService(
        dataValidator: DataValidator = this.dataValidator,
        spanFactoryDecorator: (EmbraceSpanFactory) -> EmbraceSpanFactory = { it },
    ): SpanServiceImpl {
        val otelSdkConfig = OtelSdkConfig(
            spanRepository = spanRepository,
            logSink = LogSinkImpl(),
            sdkName = "test-sdk",
            sdkVersion = "1.0",
            appVersion = "1.0.0",
            packageName = "com.test.app",
            systemInfo = SystemInfo(),
            uuidSource = TestUuidSource(),
            sessionIdsProvider = { FakeSessionIdsProvider(userSessionId = "fake-session-id") },
            processIdentifierProvider = { "fake-pid" },
        )
        val otelSdkWrapper = OtelSdkWrapper(
            otelClock = otelClock,
            configuration = otelSdkConfig,
            spanService = FakeSpanService(),
            useKotlinSdk = TESTS_DEFAULT_USE_KOTLIN_SDK,
        )
        val embraceSpanFactory = EmbraceSpanFactoryImpl(
            openTelemetryClock = otelClock,
            spanRepository = spanRepository,
            dataValidator = dataValidator,
            telemetryService = FakeTelemetryService(),
        )

        return SpanServiceImpl(
            spanRepository = spanRepository,
            dataValidator = dataValidator,
            canStartNewSpan = ::canStartNewSpan,
            initCallback = ::initCallback,
            embraceSpanFactory = spanFactoryDecorator(embraceSpanFactory),
            tracerSupplier = {
                if (failNextTracerLookup) {
                    failNextTracerLookup = false
                    error("OTel SDK is not ready")
                }
                otelSdkWrapper.sdkTracer
            },
            openTelemetrySupplier = { otelSdkWrapper.openTelemetryKotlin },
        )
    }

    private fun verifyAndReturnSoleCompletedSpan(name: String): Span {
        val currentSpans = spanRepository.completedOtelSpans()
        assertEquals(1, currentSpans.size)
        assertEquals(name, currentSpans[0].name)
        return currentSpans[0]
    }

    @Suppress("UNUSED_PARAMETER")
    private fun canStartNewSpan(parentSpan: EmbraceSpan?, internal: Boolean, type: EmbType): Boolean {
        return spanCreationAllowed
    }

    private fun initCallback(initTimeMs: Long) {
        this.initTimeMs = initTimeMs
        initCallbackCount++
    }

    /**
     * Widens the window in which the service is replaying buffered spans, so that a racing caller can reliably be observed.
     */
    private class SlowEmbraceSpanFactory(
        private val delegate: EmbraceSpanFactory,
        private val replayStarted: CountDownLatch,
    ) : EmbraceSpanFactory {
        override fun create(otelSpanStartArgs: OtelSpanStartArgs): EmbraceSdkSpan {
            if (replayStarted.count > 0) {
                replayStarted.countDown()
                Thread.sleep(200L)
            }
            return delegate.create(otelSpanStartArgs)
        }
    }

    private companion object {
        /**
         * Mirrors the private cap in [SpanServiceImpl] on the number of pre-initialization calls that will be buffered.
         */
        private const val MAX_BUFFERED_CALLS = 1000
        private const val SERVICE_INIT_TIMEOUT_MS = 5000L
    }
}

package io.embrace.android.embracesdk.internal.otel.spans

import androidx.test.ext.junit.runners.AndroidJUnit4
import io.embrace.android.embracesdk.assertions.assertError
import io.embrace.android.embracesdk.assertions.assertIsPrivateSpan
import io.embrace.android.embracesdk.assertions.assertIsType
import io.embrace.android.embracesdk.assertions.assertIsTypePerformance
import io.embrace.android.embracesdk.assertions.assertNotPrivateSpan
import io.embrace.android.embracesdk.fakes.FakeClock
import io.embrace.android.embracesdk.fakes.FakeEventService
import io.embrace.android.embracesdk.fakes.FakeOtelKotlinClock
import io.embrace.android.embracesdk.fakes.FakeSpanService
import io.embrace.android.embracesdk.fakes.TestConstants.TESTS_DEFAULT_USE_KOTLIN_SDK
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
import io.embrace.android.embracesdk.internal.arch.schema.EmbType
import io.embrace.android.embracesdk.internal.clock.nanosToMillis
import io.embrace.android.embracesdk.internal.otel.config.OtelSdkConfig
import io.embrace.android.embracesdk.internal.otel.logs.LogSinkImpl
import io.embrace.android.embracesdk.internal.otel.sdk.DataValidator
import io.embrace.android.embracesdk.internal.otel.sdk.OtelSdkWrapper
import io.embrace.android.embracesdk.internal.otel.sdk.id.OtelIds
import io.embrace.android.embracesdk.spans.EmbraceSpan
import io.embrace.android.embracesdk.spans.EmbraceSpanEvent
import io.embrace.android.embracesdk.spans.ErrorCode
import io.embrace.opentelemetry.kotlin.ExperimentalApi
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@OptIn(ExperimentalApi::class)
@RunWith(AndroidJUnit4::class)
internal class SpanServiceImplTest {
    private lateinit var spanSink: SpanSink
    private lateinit var dataValidator: DataValidator
    private lateinit var spansService: SpanServiceImpl
    private val clock = FakeClock()
    private var spanCreationAllowed: Boolean = true
    private var initTimeMs: Long = 0L

    @Before
    fun setup() {
        spanSink = SpanSinkImpl()
        dataValidator = DataValidator()
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
            spansService.createSpan(name = "test-span", internal = true, private = false)
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
            spansService.createSpan(name = "test-span", internal = false, private = true)
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
            )
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

        val currentSpans = spanSink.completedSpans()
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
        spanSink.flushSpans()
        val parentStartTime = clock.now()
        val parent = checkNotNull(spansService.startSpan(name = "test-span", private = false))
        val childStartTimeMs = clock.now() + 10L
        val child = checkNotNull(
            spansService.startSpan(
                name = "child-span",
                parent = parent,
                startTimeMs = childStartTimeMs,
                type = EmbType.Ux.View,
            )
        )
        clock.tick(40L)
        val childSpanEndTimeMs = clock.now()
        assertTrue(child.stop())
        with(spanSink.flushSpans().single()) {
            assertEquals("emb-child-span", name)
            assertEquals(childStartTimeMs, startTimeNanos.nanosToMillis())
            assertEquals(childSpanEndTimeMs, endTimeNanos.nanosToMillis())
            assertNotPrivateSpan()
            assertIsType(EmbType.Ux.View)
        }
        clock.tick(10)
        val parentEndTime = clock.now()
        assertTrue(parent.stop())
        with(spanSink.flushSpans().single()) {
            assertEquals("emb-test-span", name)
            assertEquals(parentStartTime, startTimeNanos.nanosToMillis())
            assertEquals(parentEndTime, endTimeNanos.nanosToMillis())
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
            Pair("attribute2", "value2")
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
            events = expectedEvents,
        )

        with(verifyAndReturnSoleCompletedSpan("emb-$expectedName")) {
            assertEquals(expectedStartTimeMs, startTimeNanos.nanosToMillis())
            assertEquals(expectedEndTimeMs, endTimeNanos.nanosToMillis())
            assertIsTypePerformance()
            assertEquals(OtelIds.INVALID_SPAN_ID, parentSpanId)
            assertNotPrivateSpan()
            expectedAttributes.forEach {
                assertEquals(it.value, attributes[it.key])
            }
            assertEquals(expectedEvents, events)
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
            )
        )

        with(verifyAndReturnSoleCompletedSpan("emb-$expectedName")) {
            assertEquals(expectedStartTimeMs, startTimeNanos.nanosToMillis())
            assertEquals(expectedEndTimeMs, endTimeNanos.nanosToMillis())
            assertNotPrivateSpan()
        }
        assertTrue(parentSpan.stop())

        val currentSpans = spanSink.completedSpans()
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
                    errorCode = errorCode,
                )
            )
            with(verifyAndReturnSoleCompletedSpan("emb-test${errorCode.name}")) {
                assertError(errorCode)
            }
            spanSink.flushSpans()
        }
    }

    @Test
    fun `validate start and end times for a completed span`() {
        assertFalse(
            spansService.recordCompletedSpan(
                name = "test-pan",
                startTimeMs = 500,
                endTimeMs = 499,
            )
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

        val currentSpans = spanSink.completedSpans()
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
        assertEquals(0, spanSink.completedSpans().size)
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
            )
        )
        assertNotNull(spansService.recordSpan(name = TOO_LONG_SPAN_NAME, internal = false) { 1 })
        assertEquals(2, spanSink.completedSpans().size)
        assertNotNull(spansService.createSpan(name = MAX_LENGTH_SPAN_NAME, internal = false))
        assertNotNull(spansService.recordSpan(name = MAX_LENGTH_SPAN_NAME, internal = false) { 2 })
        assertTrue(
            spansService.recordCompletedSpan(
                name = MAX_LENGTH_SPAN_NAME,
                startTimeMs = 100L,
                endTimeMs = 200L,
                internal = false,
            )
        )
        assertEquals(4, spanSink.completedSpans().size)
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
            )
        )
        assertTrue(
            spansService.recordCompletedSpan(
                name = MAX_LENGTH_INTERNAL_SPAN_NAME,
                startTimeMs = 100L,
                endTimeMs = 200L,
                internal = true,
                attributes = tooBigSystemAttributes
            )
        )
        assertTrue(
            spansService.recordCompletedSpan(
                name = MAX_LENGTH_INTERNAL_SPAN_NAME,
                startTimeMs = 100L,
                endTimeMs = 200L,
                internal = true,
                events = tooBigSystemEvents
            )
        )
        assertNotNull(
            spansService.recordSpan(name = TOO_LONG_INTERNAL_SPAN_NAME, internal = true) {
                1
            }
        )
        assertNotNull(spansService.createSpan(name = MAX_LENGTH_INTERNAL_SPAN_NAME, internal = true))
        assertNotNull(
            spansService.recordSpan(name = MAX_LENGTH_INTERNAL_SPAN_NAME, internal = true) {
                2
            }
        )
        assertTrue(
            spansService.recordCompletedSpan(
                name = MAX_LENGTH_INTERNAL_SPAN_NAME,
                startTimeMs = 100L,
                endTimeMs = 200L,
                internal = true,
                attributes = maxSizeSystemAttributes,
                events = maxSizeSystemEvents
            )
        )
        val completedSpans = spanSink.completedSpans()
        assertEquals(6, completedSpans.size)
        assertEquals(2, completedSpans.filter { it.name.endsWith("...") }.size)
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
            )
        )
        assertTrue(
            spansService.recordCompletedSpan(
                name = MAX_LENGTH_SPAN_NAME,
                startTimeMs = 100L,
                endTimeMs = 200L,
                internal = false,
                events = maxSizeCustomEvents,
            )
        )

        assertEquals(maxEventAttrCount, spanSink.flushSpans().single { it.name == "too many events" }.events.size)

        val attributesMap = mutableMapOf(
            Pair(TOO_LONG_ATTRIBUTE_KEY, "value"),
            Pair("key", TOO_LONG_ATTRIBUTE_VALUE),
        )
        repeat(8) {
            attributesMap["key$it"] = "value"
        }

        val events = mutableListOf(checkNotNull(EmbraceSpanEvent.create("event", 100L, attributesMap)))
        repeat(dataValidator.otelLimitsConfig.getMaxCustomEventCount() - 1) {
            events.add(checkNotNull(EmbraceSpanEvent.create("event", 100L, null)))
        }
        assertTrue(
            spansService.recordCompletedSpan(
                name = MAX_LENGTH_SPAN_NAME,
                startTimeMs = 100L,
                endTimeMs = 200L,
                internal = false,
                events = events,
            )
        )

        val completedSpans = spanSink.completedSpans()
        assertEquals(1, completedSpans.size)
        assertEquals(10, completedSpans[0].events.size)
        assertEquals(maxEventAttrCount, completedSpans[0].events[0].attributes.size)
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
            )
        )
        assertTrue(
            spansService.recordCompletedSpan(
                name = MAX_LENGTH_SPAN_NAME,
                startTimeMs = 100L,
                endTimeMs = 200L,
                internal = false,
                attributes = maxSizeCustomAttributes,
            )
        )

        spanSink.flushSpans()

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
            )
        )

        val truncatedAttributesSpan = spanSink.completedSpans().single { it.name == MAX_LENGTH_SPAN_NAME }
        val attrs = truncatedAttributesSpan.attributes
        val truncatedAttrCount = attrs.filter { it.key.startsWith("sss") }.size
        assertEquals(2, truncatedAttrCount)
        assertEquals(98, attrs.filter { it.key.startsWith("test-key") }.size)
    }

    @Test
    fun `bypass validation for non-internal spans`() {
        spansService = createSpanService(DataValidator(bypassValidation = { true }))

        assertNotNull(spansService.createSpan(name = TOO_LONG_SPAN_NAME, internal = false))
        assertTrue(
            spansService.recordCompletedSpan(
                name = TOO_LONG_SPAN_NAME,
                startTimeMs = 100L,
                endTimeMs = 200L,
                internal = false,
            )
        )
        assertTrue(
            spansService.recordCompletedSpan(
                name = "too many events",
                startTimeMs = 100L,
                endTimeMs = 200L,
                internal = false,
                events = tooBigCustomEvents,
            )
        )
        assertTrue(
            spansService.recordCompletedSpan(
                name = "too many attributes",
                startTimeMs = 100L,
                endTimeMs = 200L,
                internal = false,
                attributes = tooBigCustomAttributes,
            )
        )
    }

    @Test
    fun `validation for internal spans still enforced even when non-internal limits bypassed`() {
        spansService = createSpanService(DataValidator(bypassValidation = { true }))

        assertNotNull(spansService.createSpan(name = TOO_LONG_INTERNAL_SPAN_NAME, internal = true))
        assertTrue(
            spansService.recordCompletedSpan(
                name = TOO_LONG_INTERNAL_SPAN_NAME,
                startTimeMs = 100L,
                endTimeMs = 200L,
                internal = true,
            )
        )
        assertTrue(
            spansService.recordCompletedSpan(
                name = "too many events",
                startTimeMs = 100L,
                endTimeMs = 200L,
                internal = true,
                events = tooBigSystemEvents,
            )
        )
        assertTrue(
            spansService.recordCompletedSpan(
                name = "too many attributes",
                startTimeMs = 100L,
                endTimeMs = 200L,
                internal = true,
                attributes = tooBigSystemAttributes,
            )
        )

        assertEquals(3, spanSink.completedSpans().size)
    }

    private fun createSpanService(dataValidator: DataValidator): SpanServiceImpl {
        val fakeClock = FakeOtelKotlinClock(clock)
        val otelSdkConfig = OtelSdkConfig(
            spanSink = spanSink,
            logSink = LogSinkImpl(),
            sdkName = "test-sdk",
            sdkVersion = "1.0",
            appVersion = "1.0.0",
            packageName = "com.test.app",
            systemInfo = SystemInfo(),
            sessionIdProvider = { "fake-session-id" },
            processIdentifierProvider = { "fake-pid" }
        )
        val otelSdkWrapper = OtelSdkWrapper(
            otelClock = fakeClock,
            configuration = otelSdkConfig,
            spanService = FakeSpanService(),
            eventService = FakeEventService(),
            useKotlinSdk = TESTS_DEFAULT_USE_KOTLIN_SDK,
        )

        return SpanServiceImpl(
            tracer = otelSdkWrapper.sdkTracer,
            spanRepository = SpanRepository(),
            embraceSpanFactory = EmbraceSpanFactoryImpl(
                openTelemetryClock = fakeClock,
                spanRepository = SpanRepository(),
                dataValidator = dataValidator
            ),
            dataValidator = dataValidator,
            canStartNewSpan = ::canStartNewSpan,
            initCallback = ::initCallback,
            openTelemetry = otelSdkWrapper.openTelemetryKotlin
        ).apply {
            initializeService(fakeClock.now().nanosToMillis())
        }
    }

    private fun verifyAndReturnSoleCompletedSpan(name: String): EmbraceSpanData {
        val currentSpans = spanSink.completedSpans()
        assertEquals(1, currentSpans.size)
        assertEquals(name, currentSpans[0].name)
        return currentSpans[0]
    }

    @Suppress("UNUSED_PARAMETER")
    private fun canStartNewSpan(parentSpan: EmbraceSpan?, internal: Boolean): Boolean {
        return spanCreationAllowed
    }

    private fun initCallback(initTimeMs: Long) {
        this.initTimeMs = initTimeMs
    }
}

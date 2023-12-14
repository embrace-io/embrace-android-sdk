package io.embrace.android.embracesdk.internal.spans

import io.embrace.android.embracesdk.config.remote.SpansRemoteConfig
import io.embrace.android.embracesdk.fakes.FakeClock
import io.embrace.android.embracesdk.fakes.FakeConfigService
import io.embrace.android.embracesdk.fakes.FakeTelemetryService
import io.embrace.android.embracesdk.fakes.fakeSpansBehavior
import io.embrace.android.embracesdk.internal.OpenTelemetryClock
import io.embrace.android.embracesdk.spans.EmbraceSpanEvent
import io.opentelemetry.sdk.common.CompletableResultCode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

internal class EmbraceSpansServiceTest {

    private lateinit var spansRemoteConfig: SpansRemoteConfig
    private lateinit var configService: FakeConfigService
    private lateinit var spansService: EmbraceSpansService
    private val clock = FakeClock(10000L)

    @Before
    fun setup() {
        spansRemoteConfig = SpansRemoteConfig()
        configService = FakeConfigService(
            spansBehavior = fakeSpansBehavior { spansRemoteConfig }
        )
        spansService = EmbraceSpansService(
            clock = OpenTelemetryClock(clock),
            telemetryService = FakeTelemetryService()
        )
        configService.addListener(spansService)
    }

    @Test
    fun `verify default behaviour before initialization`() {
        assertFalse(spansService.initialized())
        assertNull(spansService.createSpan("test-span"))
        assertTrue(spansService.recordCompletedSpan("test-span", 10, 20))
        var lambdaRan = false
        spansService.recordSpan("test-span") { lambdaRan = true }
        assertTrue(lambdaRan)
        assertNull(spansService.completedSpans())
        assertNull(spansService.flushSpans())
        assertEquals(CompletableResultCode.ofFailure(), spansService.storeCompletedSpans(listOf()))
    }

    @Test
    fun `initializing service if the config is not on won't actually initialize it`() {
        spansRemoteConfig = SpansRemoteConfig(pctEnabled = 0f)
        configService.updateListeners()
        spansService.initializeService(1, 5)
        configService.updateListeners()
        assertFalse(spansService.initialized())
    }

    @Test
    fun `service works once initialized`() {
        initializeServiceThenEnableConfig()
        assertTrue(spansService.initialized())
        assertTrue(spansService.recordCompletedSpan("test-span", 10, 20))
        var lambdaRan = false
        spansService.recordSpan("test-span") { lambdaRan = true }
        assertTrue(lambdaRan)
        assertEquals(3, spansService.completedSpans()?.size)
        assertEquals(4, spansService.flushSpans()?.size)
    }

    @Test
    fun `service can be initialized after config enabled`() {
        spansService.initializeService(1, 5)
        spansRemoteConfig = SpansRemoteConfig(pctEnabled = 100f)
        configService.updateListeners()
        assertTrue(spansService.initialized())
        assertEquals(1, spansService.completedSpans()?.size)
    }

    @Test
    fun `second sdk startup span will not be recorded if you try to initialize the service twice`() {
        initializeServiceThenEnableConfig()
        assertEquals(1, spansService.completedSpans()?.size)
        spansService.initializeService(10, 20)
        assertEquals(1, spansService.completedSpans()?.size)
    }

    @Test
    fun `record internal completed span recording with all the fixings`() {
        initializeServiceThenEnableConfig()
        spansService.flushSpans()
        val expectedName = "test-span"
        val expectedStartTime = clock.now()
        val expectedEndTime = expectedStartTime + 100L
        val expectedType = EmbraceAttributes.Type.PERFORMANCE
        val expectedAttributes = mapOf(
            Pair("attribute1", "value1"),
            Pair("attribute2", "value2")
        )
        val expectedEvents = listOf(
            EmbraceSpanEvent(name = "event1", timestampNanos = 0L, attributes = expectedAttributes),
            EmbraceSpanEvent(name = "event2", timestampNanos = 5L, attributes = expectedAttributes)
        )

        spansService.recordCompletedSpan(
            name = expectedName,
            startTimeNanos = expectedStartTime,
            endTimeNanos = expectedEndTime,
            type = expectedType,
            attributes = expectedAttributes,
            events = expectedEvents
        )

        val name = "emb-$expectedName"
        val currentSpans = checkNotNull(spansService.completedSpans())
        assertEquals(1, currentSpans.size)
        val span = currentSpans[0]

        with(span) {
            assertEquals(name, name)
            assertEquals(expectedStartTime, startTimeNanos)
            assertEquals(expectedEndTime, endTimeNanos)
            assertEquals(expectedType.name, attributes[EmbraceAttributes.Type.PERFORMANCE.keyName()])
            expectedAttributes.forEach {
                assertEquals(it.value, attributes[it.key])
            }
            assertEquals(expectedEvents, events)
        }
    }

    @Test
    fun `can create spans after init`() {
        initializeServiceThenEnableConfig()
        spansService.flushSpans()
        val parent = checkNotNull(spansService.createSpan("test-span"))
        assertTrue(parent.start())
        val child = checkNotNull(spansService.createSpan(name = "test-span", parent = parent))
        assertTrue(child.start())
        assertTrue(parent.traceId == child.traceId)
        assertTrue(parent.spanId == checkNotNull(child.parent).spanId)
    }

    @Test
    fun `can record completed span after init`() {
        initializeServiceThenEnableConfig()
        spansService.flushSpans()
        val expectedName = "test-span"
        val expectedStartTime = clock.now()
        val expectedEndTime = expectedStartTime + 100L
        assertTrue(
            spansService.recordCompletedSpan(
                name = expectedName,
                startTimeNanos = expectedStartTime,
                endTimeNanos = expectedEndTime
            )
        )

        assertEquals(1, checkNotNull(spansService.completedSpans()).size)
    }

    @Test
    fun `can record completed child span after init`() {
        initializeServiceThenEnableConfig()
        spansService.flushSpans()
        val expectedName = "child-span"
        val expectedStartTime = clock.now()
        val expectedEndTime = expectedStartTime + 100L
        val parentSpan = checkNotNull(spansService.createSpan(name = "test-span"))
        assertTrue(parentSpan.start())
        assertTrue(
            spansService.recordCompletedSpan(
                name = expectedName,
                parent = parentSpan,
                startTimeNanos = expectedStartTime,
                endTimeNanos = expectedEndTime
            )
        )
        assertTrue(parentSpan.stop())

        val currentSpans = checkNotNull(spansService.completedSpans())
        assertEquals(2, currentSpans.size)
        assertTrue(currentSpans[0].traceId == currentSpans[1].traceId)
        assertTrue(currentSpans[0].parentSpanId == currentSpans[1].spanId)
    }

    @Test
    fun `can record span after init`() {
        initializeServiceThenEnableConfig()
        spansService.flushSpans()
        spansService.recordSpan(name = "test-span") {
            spansService.hashCode()
        }

        assertEquals(1, checkNotNull(spansService.completedSpans()).size)
    }

    @Test
    fun `can record child span after init`() {
        initializeServiceThenEnableConfig()
        spansService.flushSpans()
        val parent = checkNotNull(spansService.createSpan("test-span"))
        assertTrue(parent.start())
        spansService.recordSpan(name = "child-span", parent = parent) {
            spansService.hashCode()
        }
        assertTrue(parent.stop())

        val currentSpans = checkNotNull(spansService.completedSpans())
        assertEquals(2, currentSpans.size)
        assertTrue(currentSpans[0].traceId == currentSpans[1].traceId)
        assertTrue(currentSpans[0].parentSpanId == currentSpans[1].spanId)
    }

    @Test
    fun `completed spans recorded before initialization will saved and recorded upon initialization`() {
        assertFalse(spansService.initialized())
        assertTrue(spansService.recordCompletedSpan("test-span", 10, 20))
        assertTrue(spansService.recordCompletedSpan("test-span", 15, 25))
        initializeServiceThenEnableConfig()
        assertEquals(3, spansService.completedSpans()?.size)
    }

    private fun initializeServiceThenEnableConfig() {
        spansRemoteConfig = SpansRemoteConfig(pctEnabled = 100f)
        configService.updateListeners()
        spansService.initializeService(1, 5)
    }
}

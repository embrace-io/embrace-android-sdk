package io.embrace.android.embracesdk.internal.otel.spans

import io.embrace.android.embracesdk.assertions.assertIsTypePerformance
import io.embrace.android.embracesdk.fakes.FakeClock
import io.embrace.android.embracesdk.fakes.FakeEmbraceSpanFactory
import io.embrace.android.embracesdk.fakes.FakeEventService
import io.embrace.android.embracesdk.fakes.FakeOtelKotlinClock
import io.embrace.android.embracesdk.fakes.FakeSpanService
import io.embrace.android.embracesdk.fakes.FakeTelemetryService
import io.embrace.android.embracesdk.fakes.TestConstants.TESTS_DEFAULT_USE_KOTLIN_SDK
import io.embrace.android.embracesdk.fakes.fakeOpenTelemetry
import io.embrace.android.embracesdk.internal.SystemInfo
import io.embrace.android.embracesdk.internal.arch.schema.EmbType
import io.embrace.android.embracesdk.internal.clock.nanosToMillis
import io.embrace.android.embracesdk.internal.otel.config.OtelSdkConfig
import io.embrace.android.embracesdk.internal.otel.logs.LogSinkImpl
import io.embrace.android.embracesdk.internal.otel.sdk.DataValidator
import io.embrace.android.embracesdk.internal.otel.sdk.OtelSdkWrapper
import io.embrace.android.embracesdk.spans.EmbraceSpan
import io.embrace.android.embracesdk.spans.EmbraceSpanEvent
import io.embrace.opentelemetry.kotlin.ExperimentalApi
import io.embrace.opentelemetry.kotlin.tracing.Tracer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalApi::class)
internal class EmbraceSpanServiceTest {
    private lateinit var spanSink: SpanSink
    private lateinit var spanService: SpanService
    private lateinit var clock: FakeOtelKotlinClock
    private lateinit var tracer: Tracer
    private var spanCreationAllowed: Boolean = true
    private var initTimeMs: Long = 0L

    @Before
    fun setup() {
        clock = FakeOtelKotlinClock(FakeClock(10000L))
        val fakeClock = FakeOtelKotlinClock(FakeClock())
        spanSink = SpanSinkImpl()

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
        tracer = otelSdkWrapper.sdkTracer
        spanService = createEmbraceSpanService()
        spanService.initializeService(clock.now().nanosToMillis())
    }

    private fun createEmbraceSpanService(): EmbraceSpanService {
        val dataValidator = DataValidator(telemetryService = FakeTelemetryService())

        return EmbraceSpanService(
            spanRepository = SpanRepository(),
            canStartNewSpan = ::canStartNewSpan,
            initCallback = ::initCallback,
            embraceSpanFactorySupplier = {
                EmbraceSpanFactoryImpl(
                    openTelemetryClock = clock,
                    spanRepository = SpanRepository(),
                    dataValidator = dataValidator,
                    telemetryService = FakeTelemetryService()
                )
            },
            dataValidator = dataValidator,
            tracerSupplier = { tracer },
            openTelemetrySupplier = { fakeOpenTelemetry() }
        )
    }

    @Test
    fun `verify default behaviour before initialization`() {
        val uninitializedService = EmbraceSpanService(
            spanRepository = SpanRepository(),
            dataValidator = DataValidator(telemetryService = FakeTelemetryService()),
            canStartNewSpan = ::canStartNewSpan,
            initCallback = ::initCallback,
            embraceSpanFactorySupplier = { FakeEmbraceSpanFactory() },
            tracerSupplier = { tracer },
            openTelemetrySupplier = { fakeOpenTelemetry() }
        )
        assertFalse(uninitializedService.initialized())
        assertEquals(NoopEmbraceSdkSpan, uninitializedService.createSpan("test-span"))
        assertEquals(NoopEmbraceSdkSpan, uninitializedService.startSpan("test-span"))
        assertTrue(uninitializedService.recordCompletedSpan("test-span", 10, 20))
        var lambdaRan = false
        uninitializedService.recordSpan("test-span") { lambdaRan = true }
        assertTrue(lambdaRan)
    }

    @Test
    fun `service works once initialized`() {
        assertTrue(spanService.initialized())
        assertNotNull(spanService.createSpan("test-span"))
        assertNotNull(spanService.startSpan("test-span"))
        assertTrue(spanService.recordCompletedSpan("test-span", 10, 20))
        var lambdaRan = false
        spanService.recordSpan("test-span") { lambdaRan = true }
        assertTrue(lambdaRan)
        assertEquals(2, spanSink.completedSpans().size)
    }

    @Test
    fun `record internal completed span recording with all the fixings`() {
        spanSink.flushSpans()
        val expectedName = "test-span"
        val expectedStartTimeMs = clock.now().nanosToMillis()
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

        spanService.recordCompletedSpan(
            name = expectedName,
            startTimeMs = expectedStartTimeMs,
            endTimeMs = expectedEndTimeMs,
            type = expectedType,
            attributes = expectedAttributes,
            events = expectedEvents,
        )

        val name = "emb-$expectedName"
        val currentSpans = spanSink.completedSpans()
        assertEquals(1, currentSpans.size)
        val span = currentSpans[0]

        with(span) {
            assertEquals(name, name)
            assertEquals(expectedStartTimeMs, startTimeNanos.nanosToMillis())
            assertEquals(expectedEndTimeMs, endTimeNanos.nanosToMillis())
            assertIsTypePerformance()
            expectedAttributes.forEach {
                assertEquals(it.value, attributes[it.key])
            }
            assertEquals(expectedEvents, events)
        }
    }

    @Test
    fun `completed spans recorded before initialization will saved and recorded upon initialization`() {
        val service = createEmbraceSpanService()
        assertFalse(service.initialized())
        assertTrue(service.recordCompletedSpan("test-span", 10, 20))
        assertTrue(service.recordCompletedSpan("test-span", 15, 25))
        service.initializeService(clock.now().nanosToMillis())
        assertEquals(2, spanSink.completedSpans().size)
    }

    @Test
    fun `verify ceiling to how many recordCompleteSpan calls can be buffered`() {
        val service = createEmbraceSpanService()
        repeat(1000) {
            assertTrue(service.recordCompletedSpan("test-span", 10, 20))
        }
        assertFalse(service.recordCompletedSpan("test-span", 10, 20))
    }

    @Suppress("UNUSED_PARAMETER")
    private fun canStartNewSpan(parentSpan: EmbraceSpan?, internal: Boolean): Boolean {
        return spanCreationAllowed
    }

    private fun initCallback(initTimeMs: Long) {
        this.initTimeMs = initTimeMs
    }
}

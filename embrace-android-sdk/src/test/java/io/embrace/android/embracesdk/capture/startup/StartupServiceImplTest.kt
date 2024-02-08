package io.embrace.android.embracesdk.capture.startup

import io.embrace.android.embracesdk.fakes.FakeClock
import io.embrace.android.embracesdk.fakes.injection.FakeInitModule
import io.embrace.android.embracesdk.internal.clock.millisToNanos
import io.embrace.android.embracesdk.internal.spans.SpansService
import io.embrace.android.embracesdk.internal.spans.SpansSink
import io.embrace.android.embracesdk.internal.spans.isPrivate
import io.opentelemetry.api.trace.SpanId
import io.opentelemetry.api.trace.StatusCode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

internal class StartupServiceImplTest {

    private lateinit var spansSink: SpansSink
    private lateinit var spansService: SpansService
    private lateinit var startupService: StartupService
    private lateinit var clock: FakeClock

    @Before
    fun setUp() {
        clock = FakeClock(10000000)
        val initModule = FakeInitModule(clock = clock)
        spansSink = initModule.openTelemetryModule.spansSink
        spansService = initModule.openTelemetryModule.spansService
        spansService.initializeService(clock.nowInNanos())
        startupService = StartupServiceImpl(spansService)
    }

    @Test
    fun `initialization records SDK startup span`() {
        val startTimeMillis = clock.now()
        clock.tick(10L)
        val endTimeMillis = clock.now()
        spansService.initializeService(startTimeMillis)
        startupService.setSdkStartupInfo(startTimeMillis, endTimeMillis)
        val currentSpans = spansSink.completedSpans()
        assertEquals(1, currentSpans.size)
        with(currentSpans[0]) {
            assertEquals("emb-sdk-init", name)
            assertEquals(SpanId.getInvalid(), parentSpanId)
            assertEquals(startTimeMillis.millisToNanos(), startTimeNanos)
            assertEquals(endTimeMillis.millisToNanos(), endTimeNanos)
            assertEquals(
                io.embrace.android.embracesdk.internal.spans.EmbraceAttributes.Type.PERFORMANCE.name,
                attributes[io.embrace.android.embracesdk.internal.spans.EmbraceAttributes.Type.PERFORMANCE.keyName()]
            )
            assertTrue(isPrivate())
            assertEquals(StatusCode.OK, status)
        }
    }

    @Test
    fun `second sdk startup span will not be recorded if you try to set the startup info twice`() {
        spansService.initializeService(10)
        startupService.setSdkStartupInfo(10, 20)
        assertEquals(1, spansSink.completedSpans().size)
        startupService.setSdkStartupInfo(10, 20)
        startupService.setSdkStartupInfo(10, 20)
        assertEquals(1, spansSink.completedSpans().size)
    }

    @Test
    fun `sdk startup span recorded if the startup info is set before span service initializes`() {
        startupService.setSdkStartupInfo(10, 20)
        spansService.initializeService(10)
        assertEquals(1, spansSink.completedSpans().size)
    }
}

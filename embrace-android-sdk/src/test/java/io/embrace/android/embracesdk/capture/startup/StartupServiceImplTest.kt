package io.embrace.android.embracesdk.capture.startup

import io.embrace.android.embracesdk.fakes.FakeClock
import io.embrace.android.embracesdk.fakes.injection.FakeInitModule
import io.embrace.android.embracesdk.internal.clock.nanosToMillis
import io.embrace.android.embracesdk.internal.spans.SpanService
import io.embrace.android.embracesdk.internal.spans.SpanSink
import io.embrace.android.embracesdk.internal.spans.isPrivate
import io.opentelemetry.api.trace.SpanId
import io.opentelemetry.api.trace.StatusCode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

internal class StartupServiceImplTest {

    private lateinit var spanSink: SpanSink
    private lateinit var spanService: SpanService
    private lateinit var startupService: StartupService
    private lateinit var clock: FakeClock

    @Before
    fun setUp() {
        clock = FakeClock(10000000)
        val initModule = FakeInitModule(clock = clock)
        spanSink = initModule.openTelemetryModule.spanSink
        spanService = initModule.openTelemetryModule.spanService
        spanService.initializeService(clock.nowInNanos())
        startupService = StartupServiceImpl(spanService)
    }

    @Test
    fun `initialization records SDK startup span`() {
        val startTimeMillis = clock.now()
        clock.tick(10L)
        val endTimeMillis = clock.now()
        spanService.initializeService(startTimeMillis)
        startupService.setSdkStartupInfo(startTimeMillis, endTimeMillis)
        val currentSpans = spanSink.completedSpans()
        assertEquals(1, currentSpans.size)
        with(currentSpans[0]) {
            assertEquals("emb-sdk-init", name)
            assertEquals(SpanId.getInvalid(), parentSpanId)
            assertEquals(startTimeMillis, startTimeNanos.nanosToMillis())
            assertEquals(endTimeMillis, endTimeNanos.nanosToMillis())
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
        spanService.initializeService(10)
        startupService.setSdkStartupInfo(10, 20)
        assertEquals(1, spanSink.completedSpans().size)
        startupService.setSdkStartupInfo(10, 20)
        startupService.setSdkStartupInfo(10, 20)
        assertEquals(1, spanSink.completedSpans().size)
    }

    @Test
    fun `sdk startup span recorded if the startup info is set before span service initializes`() {
        startupService.setSdkStartupInfo(10, 20)
        spanService.initializeService(10)
        assertEquals(1, spanSink.completedSpans().size)
    }
}

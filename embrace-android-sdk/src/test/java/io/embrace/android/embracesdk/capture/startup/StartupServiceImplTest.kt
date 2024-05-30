package io.embrace.android.embracesdk.capture.startup

import io.embrace.android.embracesdk.arch.assertIsPrivateSpan
import io.embrace.android.embracesdk.arch.assertIsTypePerformance
import io.embrace.android.embracesdk.concurrency.BlockableExecutorService
import io.embrace.android.embracesdk.fakes.FakeClock
import io.embrace.android.embracesdk.fakes.injection.FakeInitModule
import io.embrace.android.embracesdk.findAttributeValue
import io.embrace.android.embracesdk.internal.clock.nanosToMillis
import io.embrace.android.embracesdk.internal.spans.SpanService
import io.embrace.android.embracesdk.internal.spans.SpanSink
import io.embrace.android.embracesdk.worker.BackgroundWorker
import io.opentelemetry.api.trace.SpanId
import io.opentelemetry.api.trace.StatusCode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

internal class StartupServiceImplTest {

    private lateinit var spanSink: SpanSink
    private lateinit var spanService: SpanService
    private lateinit var startupService: StartupService
    private lateinit var clock: FakeClock
    private lateinit var backgroundWorker: BackgroundWorker

    @Before
    fun setUp() {
        clock = FakeClock(10000000)
        val initModule = FakeInitModule(clock = clock)
        backgroundWorker = BackgroundWorker(BlockableExecutorService())
        spanSink = initModule.openTelemetryModule.spanSink
        spanService = initModule.openTelemetryModule.spanService
        spanService.initializeService(clock.now())
        startupService = StartupServiceImpl(spanService, backgroundWorker)
    }

    @Test
    fun `initialization records SDK startup span`() {
        val startTimeMillis = clock.now()
        clock.tick(10L)
        val endTimeMillis = clock.now()
        spanService.initializeService(startTimeMillis)
        startupService.setSdkStartupInfo(
            startTimeMs = startTimeMillis,
            endTimeMs = endTimeMillis,
            endedInForeground = false,
            threadName = "main"
        )
        val currentSpans = spanSink.completedSpans()
        assertEquals(1, currentSpans.size)
        with(currentSpans[0]) {
            assertEquals("emb-sdk-init", name)
            assertEquals(SpanId.getInvalid(), parentSpanId)
            assertEquals(startTimeMillis, startTimeNanos.nanosToMillis())
            assertEquals(endTimeMillis, endTimeNanos.nanosToMillis())
            assertIsTypePerformance()
            assertIsPrivateSpan()
            assertEquals(StatusCode.OK, status)
            assertEquals("false", attributes.findAttributeValue("ended-in-foreground"))
            assertEquals("main", attributes.findAttributeValue("thread-name"))
        }
    }

    @Test
    fun `second sdk startup span will not be recorded if you try to set the startup info twice`() {
        spanService.initializeService(10)
        startupService.run {
            setSdkStartupInfo(
                startTimeMs = 10,
                endTimeMs = 20,
                endedInForeground = false,
                threadName = "main"
            )
        }
        assertEquals(1, spanSink.completedSpans().size)
        startupService.run {
            setSdkStartupInfo(
                startTimeMs = 10,
                endTimeMs = 20,
                endedInForeground = false,
                threadName = "main"
            )
        }
        assertEquals(1, spanSink.completedSpans().size)
    }

    @Test
    fun `sdk startup span recorded if the startup info is set before span service initializes`() {
        startupService.setSdkStartupInfo(10, 20, false, "main")
        spanService.initializeService(10)
        assertEquals(1, spanSink.completedSpans().size)
    }

    @Test
    fun `startup info available right after setting on the service`() {
        startupService.setSdkStartupInfo(1111L, 3222L, false, "main")
        assertEquals(1111L, startupService.getSdkInitStartMs())
        assertEquals(3222L, startupService.getSdkInitEndMs())
        assertEquals(2111L, startupService.getSdkStartupDuration(true))
        assertNull(startupService.getSdkStartupDuration(false))
    }
}

package io.embrace.android.embracesdk.capture.startup

import io.embrace.android.embracesdk.fakes.FakeClock
import io.embrace.android.embracesdk.fakes.injection.FakeInitModule
import io.embrace.android.embracesdk.injection.InitModule
import io.embrace.android.embracesdk.internal.spans.EmbraceSpansService
import io.embrace.android.embracesdk.internal.spans.isPrivate
import io.opentelemetry.api.trace.SpanId
import io.opentelemetry.api.trace.StatusCode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.concurrent.TimeUnit

internal class StartupServiceImplTest {

    private lateinit var initModule: InitModule
    private lateinit var spansService: EmbraceSpansService
    private lateinit var startupService: StartupService
    private lateinit var clock: FakeClock

    @Before
    fun setUp() {
        clock = FakeClock(10000000)
        initModule = FakeInitModule(clock = clock)
        spansService = EmbraceSpansService(
            spansSink = initModule.spansSink,
            currentSessionSpan = initModule.currentSessionSpan,
            tracer = initModule.tracer
        )
        spansService.initializeService(TimeUnit.MILLISECONDS.toNanos(clock.now()))
        startupService = StartupServiceImpl(spansService)
    }

    @Test
    fun `initialization records SDK startup span`() {
        val startTimeMillis = clock.now()
        clock.tick(10L)
        val endTimeMillis = clock.now()
        spansService.initializeService(startTimeMillis)
        startupService.setSdkStartupInfo(startTimeMillis, endTimeMillis)
        val currentSpans = checkNotNull(spansService.completedSpans())
        assertEquals(1, currentSpans.size)
        with(currentSpans[0]) {
            assertEquals("emb-sdk-init", name)
            assertEquals(SpanId.getInvalid(), parentSpanId)
            assertEquals(TimeUnit.MILLISECONDS.toNanos(startTimeMillis), startTimeNanos)
            assertEquals(TimeUnit.MILLISECONDS.toNanos(endTimeMillis), endTimeNanos)
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
        assertEquals(1, spansService.completedSpans().size)
        startupService.setSdkStartupInfo(10, 20)
        startupService.setSdkStartupInfo(10, 20)
        assertEquals(1, spansService.completedSpans().size)
    }

    @Test
    fun `sdk startup span recorded if the startup info is set before span service initializes`() {
        startupService.setSdkStartupInfo(10, 20)
        spansService.initializeService(10)
        assertEquals(1, spansService.completedSpans().size)
    }
}

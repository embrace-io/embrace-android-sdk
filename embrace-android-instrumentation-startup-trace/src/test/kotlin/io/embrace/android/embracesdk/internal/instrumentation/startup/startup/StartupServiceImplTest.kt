package io.embrace.android.embracesdk.internal.instrumentation.startup.startup

import io.embrace.android.embracesdk.fakes.FakeClock
import io.embrace.android.embracesdk.fakes.FakeTelemetryDestination
import io.embrace.android.embracesdk.fakes.fakeBackgroundWorker
import io.embrace.android.embracesdk.internal.arch.state.AppState
import io.embrace.android.embracesdk.internal.instrumentation.startup.StartupService
import io.embrace.android.embracesdk.internal.instrumentation.startup.StartupServiceImpl
import io.embrace.android.embracesdk.internal.worker.BackgroundWorker
import io.embrace.android.embracesdk.semconv.EmbAppAttributes
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

internal class StartupServiceImplTest {

    private lateinit var destination: FakeTelemetryDestination
    private lateinit var startupService: StartupService
    private lateinit var clock: FakeClock
    private lateinit var backgroundWorker: BackgroundWorker

    @Before
    fun setUp() {
        clock = FakeClock(10000000)
        backgroundWorker = fakeBackgroundWorker()
        destination = FakeTelemetryDestination()
        startupService = StartupServiceImpl(destination, appVersionStartupCounter = 3)
    }

    @Test
    fun `initialization records SDK startup span`() {
        val startTimeMillis = clock.now()
        clock.tick(10L)
        val endTimeMillis = clock.now()
        startupService.setSdkStartupInfo(
            startTimeMs = startTimeMillis,
            endTimeMs = endTimeMillis,
            endState = AppState.BACKGROUND,
            threadName = "main",
        )
        val currentSpans = destination.completedSpans()
        assertEquals(1, currentSpans.size)
        with(currentSpans[0]) {
            assertEquals("sdk-init", name)
            assertNull(parent)
            assertEquals(startTimeMillis, startTimeMs)
            assertEquals(endTimeMillis, endTimeMs)
            assertTrue(private)
            assertEquals("false", attributes["ended-in-foreground"])
            assertEquals("main", attributes["thread-name"])
            assertEquals("3", attributes[EmbAppAttributes.EMB_APP_VERSION_STARTUP_COUNTER])
        }
        assertEquals(3, startupService.getAppVersionStartupCounter())
    }

    @Test
    fun `invalid app version startup counter omitted from SDK startup span`() {
        startupService = StartupServiceImpl(destination, appVersionStartupCounter = -1)
        startupService.setSdkStartupInfo(
            startTimeMs = 10,
            endTimeMs = 20,
            endState = AppState.BACKGROUND,
            threadName = "main",
        )
        val span = destination.completedSpans().single()
        assertFalse(span.attributes.containsKey(EmbAppAttributes.EMB_APP_VERSION_STARTUP_COUNTER))
        assertNull(startupService.getAppVersionStartupCounter())
    }

    @Test
    fun `second sdk startup span will not be recorded if you try to set the startup info twice`() {
        startupService.run {
            setSdkStartupInfo(
                startTimeMs = 10,
                endTimeMs = 20,
                endState = AppState.BACKGROUND,
                threadName = "main",
            )
        }
        assertEquals(1, destination.completedSpans().size)
        startupService.run {
            setSdkStartupInfo(
                startTimeMs = 10,
                endTimeMs = 20,
                endState = AppState.BACKGROUND,
                threadName = "main",
            )
        }
        assertEquals(1, destination.completedSpans().size)
    }

    @Test
    fun `startup info available right after setting on the service`() {
        startupService.setSdkStartupInfo(1111L, 3222L, AppState.BACKGROUND, "main")
        assertEquals(1111L, startupService.getSdkInitStartMs())
        assertEquals(3222L, startupService.getSdkInitEndMs())
        assertEquals(2111L, startupService.getSdkStartupDuration())
        assertEquals(3, startupService.getAppVersionStartupCounter())
    }
}

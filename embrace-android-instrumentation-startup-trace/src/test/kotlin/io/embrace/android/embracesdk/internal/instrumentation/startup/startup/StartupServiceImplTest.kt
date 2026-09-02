package io.embrace.android.embracesdk.internal.instrumentation.startup.startup

import io.embrace.android.embracesdk.fakes.FakeClock
import io.embrace.android.embracesdk.fakes.FakeTelemetryDestination
import io.embrace.android.embracesdk.fakes.fakeBackgroundWorker
import io.embrace.android.embracesdk.internal.arch.state.ProcessState
import io.embrace.android.embracesdk.internal.instrumentation.startup.SdkInitAttributeKeys
import io.embrace.android.embracesdk.internal.instrumentation.startup.StartupService
import io.embrace.android.embracesdk.internal.instrumentation.startup.StartupServiceImpl
import io.embrace.android.embracesdk.internal.instrumentation.startup.toSdkInitDurationAttributes
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
        startupService = StartupServiceImpl(destination, appVersionStartupCounterProvider = { 3 })
    }

    @Test
    fun `initialization records SDK startup span`() {
        val startTimeMillis = clock.now()
        clock.tick(10L)
        val endTimeMillis = clock.now()
        var providerInvocationCount = 0
        startupService.setSdkStartupInfo(
            startTimeMs = startTimeMillis,
            endTimeMs = endTimeMillis,
            endState = ProcessState.BACKGROUND,
            threadName = "main",
            attributesProvider = {
                providerInvocationCount++
                mapOf("modules-init" to 100L).toSdkInitDurationAttributes() +
                    mapOf(
                        SdkInitAttributeKeys.INIT_CPU_PCT to "85",
                        SdkInitAttributeKeys.INIT_RUN_DELAY_PCT to "10",
                    )
            },
        )
        assertTrue(destination.completedSpans().isEmpty())
        startupService.recordSdkInitSpan()
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
            assertEquals("100", attributes["modules-init-duration-ms"])
            assertEquals("3", attributes[EmbAppAttributes.EMB_APP_VERSION_STARTUP_COUNTER])
            assertEquals("85", attributes[SdkInitAttributeKeys.INIT_CPU_PCT])
            assertEquals("10", attributes[SdkInitAttributeKeys.INIT_RUN_DELAY_PCT])
        }
        assertEquals(3, startupService.getAppVersionStartupCounter())

        // the built attributes are memoized: later consumers get the identical set without
        // re-invoking the provider
        assertEquals(startupService.getSdkInitAttributes(), startupService.getSdkInitAttributes())
        assertEquals(1, providerInvocationCount)
    }

    @Test
    fun `invalid app version startup counter omitted from SDK startup span`() {
        startupService = StartupServiceImpl(destination, appVersionStartupCounterProvider = { -1 })
        startupService.setSdkStartupInfo(
            startTimeMs = 10,
            endTimeMs = 20,
            endState = ProcessState.BACKGROUND,
            threadName = "main",
        )
        startupService.recordSdkInitSpan()
        val span = destination.completedSpans().single()
        assertFalse(span.attributes.containsKey(EmbAppAttributes.EMB_APP_VERSION_STARTUP_COUNTER))
        assertNull(startupService.getAppVersionStartupCounter())
    }

    @Test
    fun `second sdk startup span will not be recorded if you try to record it twice`() {
        startupService.run {
            setSdkStartupInfo(
                startTimeMs = 10,
                endTimeMs = 20,
                endState = ProcessState.BACKGROUND,
                threadName = "main",
            )
            recordSdkInitSpan()
        }
        assertEquals(1, destination.completedSpans().size)
        startupService.run {
            setSdkStartupInfo(
                startTimeMs = 20,
                endTimeMs = 30,
                endState = ProcessState.FOREGROUND,
                threadName = "main",
            )
            recordSdkInitSpan()
        }
        assertEquals(1, destination.completedSpans().size)
    }

    @Test
    fun `sdk startup span not recorded before startup info is set`() {
        startupService.recordSdkInitSpan()
        assertTrue(destination.completedSpans().isEmpty())
        startupService.setSdkStartupInfo(
            startTimeMs = 10,
            endTimeMs = 20,
            endState = ProcessState.BACKGROUND,
            threadName = "main",
        )
        startupService.recordSdkInitSpan()
        assertEquals(1, destination.completedSpans().size)
    }

    @Test
    fun `startup info available right after setting on the service`() {
        startupService.setSdkStartupInfo(1111L, 3222L, ProcessState.BACKGROUND, "main") {
            mapOf("modules-init" to 100L).toSdkInitDurationAttributes()
        }
        assertEquals(1111L, startupService.getSdkInitStartMs())
        assertEquals(3222L, startupService.getSdkInitEndMs())
        assertEquals(2111L, startupService.getSdkStartupDuration())
        assertEquals(mapOf("modules-init-duration-ms" to "100"), startupService.getSdkInitAttributes())
        assertEquals(3, startupService.getAppVersionStartupCounter())
    }
}

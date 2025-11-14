package io.embrace.android.embracesdk.internal.instrumentation.anr

import io.embrace.android.embracesdk.concurrency.BlockingScheduledExecutorService
import io.embrace.android.embracesdk.fakes.FakeClock
import io.embrace.android.embracesdk.internal.session.lifecycle.AppState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.lang.Thread.currentThread
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

/**
 * Tests for the [EmbraceAnrService] that verifies behaviour when a specific order of events happen
 */
internal class EmbraceAnrServiceTimingTest {

    private val clock = FakeClock()

    @Rule
    @JvmField
    val rule = EmbraceAnrServiceRule(
        clock = clock,
        scheduledExecutorSupplier = { BlockingScheduledExecutorService(fakeClock = clock) }
    )

    private lateinit var anrExecutorService: BlockingScheduledExecutorService

    @Before
    fun setUp() {
        anrExecutorService = checkNotNull(rule.anrExecutorService)
        anrExecutorService.execute {
            rule.anrMonitorThread = AtomicReference(currentThread())
        }
        anrExecutorService.runCurrentlyBlocked()
    }

    @Test
    fun `check ANR recovery`() {
        with(rule) {
            clock.setCurrentTime(100000L)
            anrService.startAnrCapture()
            anrExecutorService.runCurrentlyBlocked()
            targetThreadHandler.onIdleThread()
            anrExecutorService.runCurrentlyBlocked()
            repeat(20) {
                anrExecutorService.moveForwardAndRunBlocked(100L)
            }
            assertTrue(state.anrInProgress)
            targetThreadHandler.onIdleThread()
            anrExecutorService.runCurrentlyBlocked()
            assertFalse(state.anrInProgress)
        }
    }

    @Test
    fun `only one recurring heartbeat task is created after foregrounding`() {
        with(rule) {
            anrService.startAnrCapture()
            anrExecutorService.runCurrentlyBlocked()
            anrExecutorService.runCurrentlyBlocked()
            assertEquals(1, anrExecutorService.scheduledTasksCount())
            anrService.onForeground(true, clock.now() + 1)
            anrExecutorService.runCurrentlyBlocked()
            anrExecutorService.runCurrentlyBlocked()
            assertEquals(1, anrExecutorService.scheduledTasksCount())
        }
    }

    @Test
    fun `test delayed background check stops monitoring when app remains in background`() {
        with(rule) {
            // Set background state and recreate service
            fakeAppStateService.state = AppState.BACKGROUND
            recreateService()

            // Start ANR capture - this should trigger scheduleDelayedBackgroundCheck
            anrService.startAnrCapture()
            anrExecutorService.runCurrentlyBlocked()

            // Monitoring is initially started (even in background)
            assertTrue(state.started.get())

            // Advance time by 20 seconds - this should trigger the delayed background check
            anrExecutorService.moveForwardAndRunBlocked(TimeUnit.SECONDS.toMillis(20))

            // Run any pending tasks to ensure the delayed check executes
            anrExecutorService.runCurrentlyBlocked()

            // Verify that monitoring is now stopped because app is still in background
            assertFalse(state.started.get())
        }
    }

    @Test
    fun `test delayed background check does not stop monitoring when app transitions to foreground`() {
        with(rule) {
            // Set background state and recreate service
            fakeAppStateService.state = AppState.BACKGROUND
            recreateService()

            // Start ANR capture - this should trigger scheduleDelayedBackgroundCheck
            anrService.startAnrCapture()
            anrExecutorService.runCurrentlyBlocked()

            // Monitoring is initially started (even in background)
            assertTrue(state.started.get())

            // Advance time by 5 seconds
            anrExecutorService.moveForwardAndRunBlocked(TimeUnit.SECONDS.toMillis(5))

            // Transition to foreground before the 10-second delay
            fakeAppStateService.state = AppState.FOREGROUND
            anrService.onForeground(false, clock.now())
            anrExecutorService.runCurrentlyBlocked()

            // Advance time by 5 more seconds to reach the 10-second mark
            anrExecutorService.moveForwardAndRunBlocked(TimeUnit.SECONDS.toMillis(5))

            // Verify that monitoring is still active because app is now in foreground
            assertTrue(state.started.get())
        }
    }

    @Test
    fun `test delayed background check is not scheduled when app starts in foreground`() {
        with(rule) {
            // Start ANR capture - this won't trigger scheduleDelayedBackgroundCheck as the default state is foreground
            anrService.startAnrCapture()
            anrExecutorService.runCurrentlyBlocked()

            // Verify that monitoring is started
            assertTrue(state.started.get())

            // Advance time by 15 seconds to ensure any delayed check would have triggered
            anrExecutorService.moveForwardAndRunBlocked(TimeUnit.SECONDS.toMillis(15))

            // Verify that monitoring is still active (no delayed check was scheduled)
            assertTrue(state.started.get())
        }
    }
}

package io.embrace.android.embracesdk.internal.instrumentation.anr

import io.embrace.android.embracesdk.concurrency.BlockingScheduledExecutorService
import io.embrace.android.embracesdk.fakes.FakeClock
import io.embrace.android.embracesdk.internal.arch.state.AppState
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
 * Tests for the [AnrServiceImpl] that verifies behaviour when a specific order of events happen
 */
internal class AnrServiceImplTimingTest {

    private val clock = FakeClock()

    @Rule
    @JvmField
    val rule = AnrServiceRule(
        clock = clock,
        scheduledExecutorSupplier = { BlockingScheduledExecutorService(fakeClock = clock) }
    )

    private lateinit var watchdogExecutorService: BlockingScheduledExecutorService

    @Before
    fun setUp() {
        watchdogExecutorService = checkNotNull(rule.watchdogExecutorService)
        watchdogExecutorService.execute {
            rule.watchdogMonitorThread = AtomicReference(currentThread())
        }
        watchdogExecutorService.runCurrentlyBlocked()
    }

    @Test
    fun `check ANR recovery`() {
        with(rule) {
            clock.setCurrentTime(100000L)
            anrService.startCapture()
            watchdogExecutorService.runCurrentlyBlocked()
            simulateAnrRecovery()
            watchdogExecutorService.runCurrentlyBlocked()
            repeat(20) {
                watchdogExecutorService.moveForwardAndRunBlocked(100L)
            }
            assertTrue(state.threadBlockageInProgress)
            simulateAnrRecovery()
            watchdogExecutorService.runCurrentlyBlocked()
            assertFalse(state.threadBlockageInProgress)
        }
    }

    private fun AnrServiceRule<*>.simulateAnrRecovery() {
        blockedThreadDetector.onTargetThreadProcessedMessage(clock.now())
    }

    @Test
    fun `only one recurring heartbeat task is created after foregrounding`() {
        with(rule) {
            anrService.startCapture()
            watchdogExecutorService.runCurrentlyBlocked()
            watchdogExecutorService.runCurrentlyBlocked()
            assertEquals(1, watchdogExecutorService.scheduledTasksCount())
            anrService.onForeground()
            watchdogExecutorService.runCurrentlyBlocked()
            watchdogExecutorService.runCurrentlyBlocked()
            assertEquals(1, watchdogExecutorService.scheduledTasksCount())
        }
    }

    @Test
    fun `test delayed background check stops monitoring when app remains in background`() {
        with(rule) {
            // Set background state and recreate service
            fakeAppStateTracker.state = AppState.BACKGROUND
            recreateService()

            // Start ANR capture - this should trigger scheduleDelayedBackgroundCheck
            anrService.startCapture()
            watchdogExecutorService.runCurrentlyBlocked()

            // Advance time by 20 seconds - this should trigger the delayed background check
            watchdogExecutorService.moveForwardAndRunBlocked(TimeUnit.SECONDS.toMillis(20))

            // Run any pending tasks to ensure the delayed check executes
            watchdogExecutorService.runCurrentlyBlocked()
        }
    }

    @Test
    fun `test delayed background check does not stop monitoring when app transitions to foreground`() {
        with(rule) {
            // Set background state and recreate service
            fakeAppStateTracker.state = AppState.BACKGROUND
            recreateService()

            // Start ANR capture - this should trigger scheduleDelayedBackgroundCheck
            anrService.startCapture()
            watchdogExecutorService.runCurrentlyBlocked()

            // Advance time by 5 seconds
            watchdogExecutorService.moveForwardAndRunBlocked(TimeUnit.SECONDS.toMillis(5))

            // Transition to foreground before the 10-second delay
            fakeAppStateTracker.state = AppState.FOREGROUND
            anrService.onForeground()
            watchdogExecutorService.runCurrentlyBlocked()

            // Advance time by 5 more seconds to reach the 10-second mark
            watchdogExecutorService.moveForwardAndRunBlocked(TimeUnit.SECONDS.toMillis(5))
        }
    }

    @Test
    fun `test delayed background check is not scheduled when app starts in foreground`() {
        with(rule) {
            // Start ANR capture - this won't trigger scheduleDelayedBackgroundCheck as the default state is foreground
            anrService.startCapture()
            watchdogExecutorService.runCurrentlyBlocked()

            // Advance time by 15 seconds to ensure any delayed check would have triggered
            watchdogExecutorService.moveForwardAndRunBlocked(TimeUnit.SECONDS.toMillis(15))
        }
    }
}

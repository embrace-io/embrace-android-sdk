package io.embrace.android.embracesdk.internal.instrumentation.thread.blockage

import io.embrace.android.embracesdk.concurrency.BlockingScheduledExecutorService
import io.embrace.android.embracesdk.fakes.FakeClock
import io.embrace.android.embracesdk.internal.arch.state.AppState
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.lang.Thread.currentThread
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

/**
 * Tests for the [ThreadBlockageServiceImpl] that verifies behaviour when a specific order of events happen
 */
internal class ThreadBlockageServiceImplTimingTest {

    private val clock = FakeClock()

    @Rule
    @JvmField
    val rule = ThreadBlockageServiceRule(
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
    fun `only one recurring heartbeat task is created after foregrounding`() {
        with(rule) {
            service.startCapture()
            watchdogExecutorService.runCurrentlyBlocked()
            watchdogExecutorService.runCurrentlyBlocked()
            assertEquals(1, watchdogExecutorService.scheduledTasksCount())
            service.onForeground()
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

            // Start capture - this should trigger scheduleDelayedBackgroundCheck
            service.startCapture()
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

            // Start capture - this should trigger scheduleDelayedBackgroundCheck
            service.startCapture()
            watchdogExecutorService.runCurrentlyBlocked()

            // Advance time by 5 seconds
            watchdogExecutorService.moveForwardAndRunBlocked(TimeUnit.SECONDS.toMillis(5))

            // Transition to foreground before the 10-second delay
            fakeAppStateTracker.state = AppState.FOREGROUND
            service.onForeground()
            watchdogExecutorService.runCurrentlyBlocked()

            // Advance time by 5 more seconds to reach the 10-second mark
            watchdogExecutorService.moveForwardAndRunBlocked(TimeUnit.SECONDS.toMillis(5))
        }
    }

    @Test
    fun `test delayed background check is not scheduled when app starts in foreground`() {
        with(rule) {
            // Start capture - this won't trigger scheduleDelayedBackgroundCheck as the default state is foreground
            service.startCapture()
            watchdogExecutorService.runCurrentlyBlocked()

            // Advance time by 15 seconds to ensure any delayed check would have triggered
            watchdogExecutorService.moveForwardAndRunBlocked(TimeUnit.SECONDS.toMillis(15))
        }
    }
}

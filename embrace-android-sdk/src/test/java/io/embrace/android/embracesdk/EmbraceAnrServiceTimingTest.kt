package io.embrace.android.embracesdk

import io.embrace.android.embracesdk.concurrency.BlockingScheduledExecutorService
import io.embrace.android.embracesdk.fakes.FakeClock
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.lang.Thread.currentThread
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
            anrService.finishInitialization(fakeConfigService)
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
}

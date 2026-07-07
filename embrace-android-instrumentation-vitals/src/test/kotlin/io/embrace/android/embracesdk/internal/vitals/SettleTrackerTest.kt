package io.embrace.android.embracesdk.internal.vitals

import android.os.SystemClock
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.embrace.android.embracesdk.internal.vitals.fake.FakeVitalsScheduler
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.shadows.ShadowSystemClock
import java.time.Duration

@RunWith(AndroidJUnit4::class)
internal class SettleTrackerTest {

    private val scheduler = FakeVitalsScheduler()
    private val settledAt = mutableListOf<Long>()

    // A threshold the test can change to model a shrinking/growing window (e.g. smoothness held -> idle).
    private var thresholdMs = 100L
    private val settle = SettleTracker(scheduler, { thresholdMs }, settledAt::add)

    // SystemClock is paused by Robolectric until advance() moves it; baselines are relative to start.
    private val start = SystemClock.uptimeMillis()

    @Test
    fun `the first activity schedules the check and later activity only moves the baseline`() {
        settle.notifyActivity(start)
        assertTrue(scheduler.scheduled)
        assertEquals("first activity schedules once", 1, scheduler.scheduledTaskCount)

        // Further activity while scheduled must not re-post — the pending check re-evaluates at expiry.
        advance(20)
        settle.notifyActivity(start + 20)
        settle.notifyActivity(start + 40)
        assertEquals("steady-state activity does no handler work", 1, scheduler.scheduledTaskCount)
        assertEquals(start + 40, settle.lastActivityMs)
    }

    @Test
    fun `it settles only once quiet for the threshold, re-scheduling for the remainder otherwise`() {
        settle.notifyActivity(start)

        // 60ms since the baseline: under threshold -> reschedule, no settle.
        advance(60)
        scheduler.runPending()
        assertTrue(settledAt.isEmpty())
        assertTrue("re-scheduled for the remainder", scheduler.scheduled)

        // 100ms since the baseline: at threshold -> settle at the baseline.
        advance(40)
        scheduler.runPending()
        assertEquals(listOf(start), settledAt)
    }

    @Test
    fun `a moved baseline pushes the settle out`() {
        settle.notifyActivity(start)
        advance(60)
        settle.notifyActivity(start + 60) // fresh activity moves the baseline

        // 100ms after the original baseline, but only 40ms after the latest activity: not settled.
        advance(40)
        scheduler.runPending()
        assertTrue(settledAt.isEmpty())

        // 100ms after the latest activity: settles at that baseline.
        advance(60)
        scheduler.runPending()
        assertEquals(listOf(start + 60), settledAt)
    }

    @Test
    fun `reschedule re-posts the settle when the threshold shrinks`() {
        thresholdMs = 500L
        settle.notifyActivity(start) // would otherwise settle at start + 500

        // The window shrinks; reschedule re-posts at the now-earlier deadline.
        thresholdMs = 100L
        settle.reschedule()

        // Firing well before the original 500ms deadline settles, proving the re-post moved it earlier.
        advance(101)
        scheduler.runDue()
        assertEquals(listOf(start), settledAt)
    }

    @Test
    fun `reschedule is a no-op when nothing is active`() {
        settle.reschedule()
        assertFalse(scheduler.scheduled)
        assertEquals(0, scheduler.scheduledTaskCount)
    }

    @Test
    fun `cancel clears the baseline and the pending settle, and the next activity starts fresh`() {
        settle.notifyActivity(start)
        advance(30)
        settle.cancel()
        assertFalse(scheduler.scheduled)
        assertEquals(0L, settle.lastActivityMs)

        // Running the check after cancel does nothing (the tracker is inactive).
        scheduler.runPending()
        assertTrue(settledAt.isEmpty())

        // The next moment schedules anew from the new baseline.
        settle.notifyActivity(start + 30)
        assertTrue(scheduler.scheduled)
        advance(100)
        scheduler.runPending()
        assertEquals(listOf(start + 30), settledAt)
    }

    private fun advance(millis: Long) = ShadowSystemClock.advanceBy(Duration.ofMillis(millis))
}

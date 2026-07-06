package io.embrace.android.embracesdk.internal.vitals.screenload

import android.os.SystemClock
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.embrace.android.embracesdk.internal.clock.millisToNanos
import io.embrace.android.embracesdk.internal.vitals.fake.FakeVitalsScheduler
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.shadows.ShadowSystemClock
import java.time.Duration

@RunWith(AndroidJUnit4::class)
internal class ScreenLoadTrackerTest {

    private val scheduler = FakeVitalsScheduler()
    private val emitted = mutableListOf<ScreenLoadResult>()

    // Wall clock tracks uptime so a load's reported start time can be asserted against [start].
    private val tracker = ScreenLoadTracker(
        scheduler = scheduler,
        clock = { SystemClock.uptimeMillis() },
        emit = emitted::add,
        idleThresholdMs = IDLE,
        timeoutMs = TIMEOUT,
    )

    @Test
    fun `a tap, navigation, and quiet destination is a settled screen load`() {
        val start = SystemClock.uptimeMillis()
        tracker.onTap() // t=0
        advance(10)
        tracker.onNavigationStart("home")
        advance(10)
        tracker.onNavigationEnd("home") // t=20, settling
        advance(10)
        frameNow() // last frame at t=30

        // quiet for the idle threshold -> settles at the last frame
        advance(IDLE)
        scheduler.runDue()

        val result = emitted.single()
        assertEquals("home", result.screenName)
        assertEquals(ScreenLoadOutcome.SETTLED, result.outcome)
        assertEquals(30L, result.durationMs)
        assertEquals("navigation started at t=10, 10ms after the tap", 10L, result.navStartDelayMs)
        assertEquals("navigation start at t=10, navigation end at t=20", 10L, result.navDurationMs)
        assertEquals("first frame at t=30, 10ms after navigation end (t=20)", 10L, result.firstFrameDurationMs)
        assertEquals(start, result.startTimeMs)
    }

    @Test
    fun `a touchless navigation start opens the load anchored at the navigation`() {
        tracker.onNavigationStart("detail") // t=0, no preceding tap
        advance(15)
        tracker.onNavigationEnd("detail") // t=15, settling

        advance(IDLE)
        scheduler.runDue()

        val result = emitted.single()
        assertEquals("detail", result.screenName)
        assertEquals(ScreenLoadOutcome.SETTLED, result.outcome)
        assertEquals(15L, result.durationMs)
        assertEquals("no preceding tap, so no delay before the navigation start", 0L, result.navStartDelayMs)
        assertEquals(15L, result.navDurationMs)
        assertEquals("no frame arrived before the settle", 0L, result.firstFrameDurationMs)
    }

    @Test
    fun `a tap while settling ends the load as user-interrupted at that tap`() {
        tracker.onTap()
        tracker.onNavigationStart("home")
        tracker.onNavigationEnd("home") // settling
        advance(50)

        tracker.onTap() // user taps the still-settling destination at t=50

        val result = emitted.single()
        assertEquals(ScreenLoadOutcome.USER_INTERRUPTED, result.outcome)
        assertEquals(50L, result.durationMs)
        assertEquals(0L, result.navStartDelayMs)
        assertEquals("navigation ended at t=0, before the interrupting tap", 0L, result.navDurationMs)
        assertEquals("no frame arrived before the interrupting tap", 0L, result.firstFrameDurationMs)
    }

    @Test
    fun `a navigation while settling ends the in-flight load as navigation-interrupted and opens a new one`() {
        tracker.onTap() // t=0
        tracker.onNavigationStart("a")
        advance(20)
        tracker.onNavigationEnd("a") // a is settling
        advance(10)
        frameNow() // a's last frame at t=30

        // A new navigation interrupts a's settle at t=70, well after its last frame.
        advance(40)
        tracker.onNavigationStart("b") // t=70

        val first = emitted.single()
        assertEquals("a", first.screenName)
        assertEquals("a ends at its last frame (t=30), not the interrupting navigation (t=70)", 30L, first.durationMs)
        assertEquals(0L, first.navStartDelayMs)
        assertEquals("a's navigation started at t=0 and ended at t=20", 20L, first.navDurationMs)
        assertEquals("a's last frame (t=30) was 10ms after its navigation end (t=20)", 10L, first.firstFrameDurationMs)
        assertEquals(ScreenLoadOutcome.NAVIGATION_INTERRUPTED, first.outcome)

        // b settles normally as its own load, anchored at its navigation start.
        tracker.onNavigationEnd("b")
        advance(IDLE)
        scheduler.runDue()

        val second = emitted.last()
        assertEquals("b", second.screenName)
        assertEquals(0L, second.durationMs)
        assertEquals(0L, second.navStartDelayMs)
        assertEquals(0L, second.navDurationMs)
        assertEquals("b settled with no frame ever rendered", 0L, second.firstFrameDurationMs)
        assertEquals(ScreenLoadOutcome.SETTLED, second.outcome)
        assertEquals(2, emitted.size)
    }

    @Test
    fun `a destination that never goes quiet times out at the first frame after navigation end`() {
        tracker.onTap()
        tracker.onNavigationStart("anim")
        tracker.onNavigationEnd("anim") // t=0, settling

        // Continuously animating: a frame every 50ms (under the idle threshold) so the settle never fires,
        // until the timeout elapses.
        repeat(20) {
            if (emitted.isEmpty()) {
                advance(50)
                frameNow()
                scheduler.runDue()
            }
        }

        val result = emitted.single()
        assertEquals(ScreenLoadOutcome.TIMED_OUT, result.outcome)
        assertEquals("anim", result.screenName)
        assertEquals("ends at the first frame after navigation end (t=50)", 50L, result.durationMs)
        assertEquals(0L, result.navStartDelayMs)
        assertEquals("navigation ended at t=0", 0L, result.navDurationMs)
        assertEquals("first frame at t=50, 50ms after navigation end (t=0)", 50L, result.firstFrameDurationMs)
    }

    @Test
    fun `window focus extends the settle past the last content frame`() {
        tracker.onTap()
        tracker.onNavigationStart("home")
        tracker.onNavigationEnd("home") // t=0, settling
        advance(10)
        frameNow() // last content frame at t=10

        advance(50)
        tracker.onWindowFocused() // t=60: open animation finished

        // Would have settled at t=110 (last frame + idle) ending at t=10 without the focus signal.
        advance(50) // t=110
        scheduler.runDue()
        assertTrue("focus pushed the baseline out, so it has not settled yet", emitted.isEmpty())

        // settles the idle threshold after the focus instead
        advance(50) // t=160
        scheduler.runDue()

        val result = emitted.single()
        assertEquals(ScreenLoadOutcome.SETTLED, result.outcome)
        assertEquals("ends at the focus gain (t=60), not the last content frame (t=10)", 60L, result.durationMs)
        assertEquals(0L, result.navStartDelayMs)
        assertEquals("navigation ended at t=0, well before the settle", 0L, result.navDurationMs)
        assertEquals("first (and only) content frame at t=10, unaffected by the later focus gain", 10L, result.firstFrameDurationMs)
    }

    @Test
    fun `a blank navigation-end name does not clobber a known screen name`() {
        tracker.onTap()
        tracker.onNavigationStart("home")
        tracker.onNavigationEnd()

        advance(IDLE)
        scheduler.runDue()

        assertEquals("home", emitted.single().screenName)
    }

    @Test
    fun `a timeout before navigation end is an incomplete sequence and emits nothing`() {
        tracker.onTap()
        tracker.onNavigationStart("home") // confirmed, but no navigation end -> settle never armed

        advance(TIMEOUT)
        scheduler.runDue()

        assertTrue(emitted.isEmpty())
    }

    @Test
    fun `an interrupt while settling abandons the load, emitting nothing`() {
        tracker.onTap()
        tracker.onNavigationStart("home")
        tracker.onNavigationEnd("home") // settling
        frameNow()

        tracker.onInterrupt()

        advance(IDLE)
        scheduler.runDue()
        assertTrue(emitted.isEmpty())
    }

    private fun advance(millis: Long) = ShadowSystemClock.advanceBy(Duration.ofMillis(millis))

    /**
     * A frame whose vsync is the current instant.
     */
    private fun frameNow() = tracker.onFrame(SystemClock.uptimeMillis().millisToNanos())

    private companion object {
        const val IDLE = 100L
        const val TIMEOUT = 500L
    }
}

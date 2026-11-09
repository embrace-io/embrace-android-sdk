package io.embrace.android.embracesdk.internal.vitals

import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.embrace.android.embracesdk.fakes.FakeErrorHandler
import io.embrace.android.embracesdk.internal.logging.ErrorHandler
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.shadows.ShadowSystemClock
import java.time.Duration

@RunWith(AndroidJUnit4::class)
internal class MainThreadProbeTest {

    private val mainLooper = shadowOf(Looper.getMainLooper())
    private val mainHandler = Handler(Looper.getMainLooper())
    private val reports = mutableListOf<Report>()

    // Robolectric keeps the clock paused, so every expected uptime is relative to this.
    private val start = SystemClock.uptimeMillis()

    @Test
    fun `on an idle looper the probe runs at start and then once per pace, never late`() {
        createProbe(blockThresholdMs = 0).start()

        mainLooper.idleFor(Duration.ofMillis(100))

        assertEquals(onTimeRuns(0, 16, 32, 48, 64, 80, 96), reports)
    }

    @Test
    fun `a block queued ahead of the probe is reported once, ending when the probe runs`() {
        createProbe().start()
        mainHandler.post { advance(120) }

        mainLooper.idleFor(Duration.ofMillis(200))

        assertEquals(listOf(Report(endUptimeMs = start + 120, durationMs = 104)), reports)
    }

    @Test
    fun `stop removes the queued probe message and later idling reports nothing`() {
        val probe = createProbe(blockThresholdMs = 0)
        probe.start()
        mainLooper.idleFor(Duration.ofMillis(32))

        probe.stop()
        assertEquals(Duration.ZERO, mainLooper.nextScheduledTaskTime)

        mainLooper.idleFor(Duration.ofMillis(100))
        assertEquals(onTimeRuns(0, 16, 32), reports)
    }

    @Test
    fun `a restart before the old message runs leaves a single chain`() {
        val probe = createProbe(blockThresholdMs = 0)
        probe.start()
        advance(5)

        probe.stop()
        probe.start()
        mainLooper.idleFor(Duration.ofMillis(64))

        assertEquals(onTimeRuns(5, 21, 37, 53, 69), reports)
    }

    @Test
    fun `a restart while the probe message runs drops the stale generation`() {
        lateinit var probe: MainThreadProbe
        probe = createProbe(blockThresholdMs = 0) { endUptimeMs, durationMs ->
            reports += Report(endUptimeMs, durationMs)
            if (reports.size == 1) {
                probe.stop()
                probe.start()
            }
        }
        probe.start()

        mainLooper.idleFor(Duration.ofMillis(64))

        assertEquals(onTimeRuns(0, 16, 32, 48, 64), reports)
    }

    @Test
    fun `isOverdue is true once the queued message is a threshold past due, and false after stop`() {
        val probe = createProbe()
        probe.start()

        advance(49)
        assertFalse(probe.isOverdue(SystemClock.uptimeMillis()))

        advance(1)
        assertTrue(probe.isOverdue(SystemClock.uptimeMillis()))

        probe.stop()
        assertFalse(probe.isOverdue(SystemClock.uptimeMillis()))
    }

    @Test
    fun `a block callback that throws is tracked and the probe keeps running`() {
        val errorHandler = FakeErrorHandler(throwOnSdkError = false)
        createProbe(blockThresholdMs = 0, errorHandler = errorHandler) { endUptimeMs, durationMs ->
            reports += Report(endUptimeMs, durationMs)
            error("boom")
        }.start()

        mainLooper.idleFor(Duration.ofMillis(32))

        assertEquals(onTimeRuns(0, 16, 32), reports)
        assertEquals(3, errorHandler.sdkErrorMessages.size)
    }

    private fun createProbe(
        blockThresholdMs: Long = 50,
        errorHandler: ErrorHandler = FakeErrorHandler(),
        onBlock: MainThreadProbe.BlockCallback = MainThreadProbe.BlockCallback { endUptimeMs, durationMs ->
            reports += Report(endUptimeMs, durationMs)
        },
    ) = MainThreadProbe(
        looper = Looper.getMainLooper(),
        blockThresholdMs = blockThresholdMs,
        onBlock = onBlock,
        errorHandler = errorHandler,
    )

    private fun advance(millis: Long) = ShadowSystemClock.advanceBy(Duration.ofMillis(millis))

    /** Reports for runs at each of [offsetsMs] after [start], each exactly on time. */
    private fun onTimeRuns(vararg offsetsMs: Long) = offsetsMs.map { Report(endUptimeMs = start + it, durationMs = 0) }

    private data class Report(val endUptimeMs: Long, val durationMs: Long)
}

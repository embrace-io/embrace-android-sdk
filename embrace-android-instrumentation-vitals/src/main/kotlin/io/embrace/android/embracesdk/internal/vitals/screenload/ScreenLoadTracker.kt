package io.embrace.android.embracesdk.internal.vitals.screenload

import android.os.SystemClock
import android.os.SystemClock.uptimeMillis
import androidx.annotation.WorkerThread
import io.embrace.android.embracesdk.internal.clock.Clock
import io.embrace.android.embracesdk.internal.clock.nanosToMillis
import io.embrace.android.embracesdk.internal.vitals.SettleTracker
import io.embrace.android.embracesdk.internal.vitals.VitalsScheduler

/**
 * Tracks the screen-load vital: the time from a user's tap until the destination screen settles.
 *
 * All times are milliseconds relative to [SystemClock.uptimeMillis], except [ScreenLoadResult.startTimeMs] which is wall-clock epoch.
 */
internal class ScreenLoadTracker(
    private val scheduler: VitalsScheduler,
    private val clock: Clock,
    private val emit: (ScreenLoadResult) -> Unit,
    private val idleThresholdMs: Long = IDLE_THRESHOLD_MS,
    private val timeoutMs: Long = SETTLE_TIMEOUT_MS,
) {
    private val settle = SettleTracker(scheduler, { idleThresholdMs }, ::onSettled)

    private var state = State.IDLE
    private var screenName = ""
    private var startUptimeMs = 0L
    private var startWallMs = 0L

    // A continuously animating screen keeps pushing the settle baseline out, so the load would never
    // settle. This timeout Runnable force-settles the screen load with a TIMED_OUT outcome.
    private val timeoutRunnable = Runnable { onTimeout() }

    // The first frame rendered after navigation end (0 until one arrives). On a timeout this is the
    // reported end of the load - the earliest plausible "loaded" moment, since the destination never
    // actually settled.
    private var firstFrameAfterNavEndMs = 0L

    // When the settle was armed by navigation end; the timeout end falls back to this if no frame followed.
    private var navEndMs = 0L

    // When the navigation start confirmed the candidate as a real screen load.
    private var navStartMs = 0L

    /**
     * Signal a committed tap (touch up) to open a fresh **candidate** screen load (discarding any in-flight). The touch-up / tap event
     * is the "start" of a screen load duration (assuming that [onNavigationStart] confirms that this tap is a navigation event). The
     * [eventTime] can be specified relative to [uptimeMillis] to accurately anchor the screen load to the user interaction.
     *
     * If the prior load was already settling (the tap landed *after* navigation end), the user acted on a
     * screen that was interactive enough to touch: emit that load as [ScreenLoadOutcome.USER_INTERRUPTED] rather than dropping it,
     * then open the new candidate.
     */
    @WorkerThread
    fun onTap(eventTime: Long = uptimeMillis()) {
        if (state == State.SETTLING) {
            complete(endMs = eventTime, outcome = ScreenLoadOutcome.USER_INTERRUPTED)
        }
        settle.cancel()
        state = State.CANDIDATE
        screenName = ""
        startUptimeMs = eventTime

        val startUptimeDelta = uptimeMillis() - startUptimeMs
        startWallMs = clock.now() - startUptimeDelta
        scheduleTimeout()
    }

    /**
     * Signal a navigation start, which confirms a real screen load (typically including the time since [onTap]). If the destination
     * [screenName] is known here it should be specified as non-`null`. The [eventTime] can be specified relative to [uptimeMillis] to
     * accurately anchor the navigation start to the underlying event (e.g. the Activity creation), rather than to whenever this call
     * happens to run.
     */
    @WorkerThread
    fun onNavigationStart(screenName: String? = null, eventTime: Long = uptimeMillis()) {
        when (state) {
            State.IDLE -> openLoad(eventTime)
            State.CANDIDATE -> {
                if (eventTime <= startUptimeMs + NAVIGATION_TIMEOUT_MS) {
                    state = State.CONFIRMED
                    navStartMs = eventTime
                } else {
                    // the previous "tap" event is too far in the past to be considered part of the screen load
                    // so we move the "start time" to now
                    openLoad(eventTime)
                }
            }

            State.CONFIRMED -> {}
            State.SETTLING -> {
                // flush the current screen load before we start another one
                complete(endMs = settle.lastActivityMs, outcome = ScreenLoadOutcome.NAVIGATION_INTERRUPTED)
                openLoad(eventTime)
            }
        }

        // make sure that screenName isn't leftover from a previous navigation
        this.screenName = screenName ?: ""
    }

    /**
     * Signal the end of a navigation, recording that the destination has been reached (updating the screen name if [screenName] is not
     * `null`). The [eventTime] can be specified relative to [uptimeMillis] to accurately anchor the navigation end to the underlying
     * event (e.g. the Activity resume), rather than to whenever this call happens to run.
     */
    @WorkerThread
    fun onNavigationEnd(screenName: String? = null, eventTime: Long = uptimeMillis()) {
        if (state != State.CONFIRMED && state != State.SETTLING) {
            return
        }

        screenName?.let {
            this.screenName = it
        }

        if (state == State.CONFIRMED) {
            // the screen load can now be considered "settling"
            state = State.SETTLING
            navEndMs = eventTime
            firstFrameAfterNavEndMs = 0L
        }

        settle.notifyActivity(eventTime)
    }

    /** A frame on the destination pushes the settle baseline out while it keeps rendering. */
    @WorkerThread
    fun onFrame(vsyncNanos: Long) {
        if (state != State.SETTLING) {
            return
        }

        val vsyncMs = vsyncNanos.nanosToMillis()
        if (firstFrameAfterNavEndMs == 0L) {
            firstFrameAfterNavEndMs = vsyncMs
        }

        settle.notifyActivity(vsyncMs)
    }

    /**
     * The destination's window gained focus: its open animation has finished. These animations render no app frames, so [onFrame] isn't
     * called. We treat the "focused" event as activity to push the settle baseline out.
     */
    @WorkerThread
    fun onWindowFocused() {
        if (state != State.SETTLING) {
            return
        }

        settle.notifyActivity(uptimeMillis())
    }

    /**
     * The load was interrupted (e.g. backgrounded): abandon the candidate, emitting nothing.
     */
    @WorkerThread
    fun onInterrupt() {
        discard()
    }

    private fun onSettled(lastActivityMs: Long) {
        if (state != State.SETTLING) {
            return
        }

        complete(endMs = lastActivityMs, outcome = ScreenLoadOutcome.SETTLED)
    }

    /**
     * The load ran for [timeoutMs] without settling - a continuously animating destination would loop  forever otherwise. Only a load that
     * reached [State.SETTLING] (a confirmed navigation start *and* end) is a real screen load; report it ending at the first frame after
     * navigation end (falling back to the navigation-end moment if none arrived). Any other state is an incomplete sequence: discard it.
     */
    private fun onTimeout() {
        if (state != State.SETTLING) {
            discard()
            return
        }

        val endMs = if (firstFrameAfterNavEndMs != 0L) firstFrameAfterNavEndMs else navEndMs
        complete(endMs = endMs, outcome = ScreenLoadOutcome.TIMED_OUT)
    }

    private fun openLoad(eventTime: Long = uptimeMillis()) {
        state = State.CONFIRMED
        screenName = ""
        startUptimeMs = eventTime
        navStartMs = startUptimeMs

        val startUptimeDelta = uptimeMillis() - startUptimeMs
        startWallMs = clock.now() - startUptimeDelta
        scheduleTimeout()
    }

    private fun complete(endMs: Long, outcome: ScreenLoadOutcome) {
        val result = ScreenLoadResult(
            startTimeMs = startWallMs,
            navStartDelayMs = navStartMs - startUptimeMs,
            navDurationMs = navEndMs - navStartMs,
            firstFrameDurationMs = if (firstFrameAfterNavEndMs != 0L) firstFrameAfterNavEndMs - navEndMs else 0L,
            durationMs = endMs - startUptimeMs,
            screenName = screenName,
            outcome = outcome,
        )

        discard()
        emit(result)
    }

    private fun scheduleTimeout() {
        scheduler.scheduleSettle(timeoutMs, timeoutRunnable)
    }

    private fun discard() {
        state = State.IDLE
        settle.cancel()
        scheduler.cancelSettle(timeoutRunnable)
    }

    private companion object {
        /**
         * How long the UI must be idle before it is considered settled. See [SettleTracker] for details.
         */
        const val IDLE_THRESHOLD_MS = 1000L

        /**
         * Maximum time allowed for a screen to settle. Once this timeout has passed the screen load is reported with a duration that
         * does not include the settling time, but with an outcome of [ScreenLoadOutcome.TIMED_OUT].
         */
        const val SETTLE_TIMEOUT_MS = 30_000L

        /**
         * The maximum time between a tap and [onNavigationStart] for the two events to be considered "linked" within a single screen load.
         */
        const val NAVIGATION_TIMEOUT_MS = 500L
    }

    /**
     * The screen-load sequence is a strict linear progression - each step is gated on the prior one - so a
     * single state models it exactly and makes the impossible combinations unrepresentable.
     */
    private enum class State {
        /**
         * No candidate in progress.
         */
        IDLE,

        /**
         * A committed tap opened a candidate; awaiting a navigation start.
         */
        CANDIDATE,

        /**
         * A navigation start confirmed the candidate; awaiting a navigation end.
         */
        CONFIRMED,

        /**
         * A navigation end armed the settle; awaiting the destination to go quiet.
         */
        SETTLING,
    }
}

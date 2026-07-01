package io.embrace.android.embracesdk.internal.vitals.screenload

/**
 * How a screen load ended.
 */
internal enum class ScreenLoadOutcome {
    /**
     * The destination settled: it went quiet for the idle threshold within the timeout.
     */
    SETTLED,

    /**
     * The destination never settled within the timeout (e.g. continuously animating content). The load
     * is reported ending at the first frame after navigation end — the earliest plausible "loaded" moment.
     */
    TIMED_OUT,

    /**
     * The user tapped again after navigation end, while the destination was still settling — the screen
     * was interactive enough to act on, so the load is reported ending at that tap rather than waiting for
     * a settle the user pre-empted.
     */
    USER_INTERRUPTED,

    /**
     * A new (touchless) navigation started while the destination was still settling — the screen was shown
     * long enough to navigate away from, so it is treated as loaded and reported ending at its last
     * rendered activity (frame or focus gain), not the interrupting navigation moment. A fresh load opens
     * for the new destination. An interruption preceded by a fresh tap is a [USER_INTERRUPTED] instead.
     */
    NAVIGATION_INTERRUPTED,
}

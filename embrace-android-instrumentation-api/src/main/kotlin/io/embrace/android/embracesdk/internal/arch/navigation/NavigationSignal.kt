package io.embrace.android.embracesdk.internal.arch.navigation

/**
 * The raw signals published by navigation sources, named without reference to any UI framework so that a new kind of
 * source adds no shared vocabulary.
 *
 * [ActivityStarted.instanceId] and its siblings correlate the signals about one Activity instance within the process and
 * are never reported; the reported representation of a screen is always its name.
 */
sealed interface NavigationSignal {

    /**
     * The time at which the signal was detected, which downstream processing should use rather than the time it processes it.
     */
    val timestampMs: Long

    /**
     * An Activity has begun starting.
     */
    data class ActivityStarted(val instanceId: Int, override val timestampMs: Long) : NavigationSignal

    /**
     * An Activity is visible, named by itself unless a screen source is attached.
     */
    data class ActivityResumed(
        val instanceId: Int,
        val name: String,
        override val timestampMs: Long,
    ) : NavigationSignal

    /**
     * An Activity is no longer visible.
     */
    data class ActivityPaused(val instanceId: Int, override val timestampMs: Long) : NavigationSignal

    /**
     * A finer-grained source will name the screens in this Activity from now on.
     */
    data class ScreenSourceAttached(val instanceId: Int, override val timestampMs: Long) : NavigationSignal

    /**
     * The screen shown within an Activity has changed.
     */
    data class ScreenChanged(
        val instanceId: Int,
        val name: String,
        override val timestampMs: Long,
    ) : NavigationSignal

    /**
     * No Activity is visible.
     */
    data class Backgrounded(override val timestampMs: Long) : NavigationSignal
}

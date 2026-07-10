package io.embrace.android.embracesdk.internal.vitals

/**
 * Receives the raw signals captured by the Vitals instrumentation. Implemented by the shared tracker that
 * drives both the smoothness and screen-load vitals.
 */
internal interface FocalInteractionCallbacks {

    /**
     * A frame was redrawn: [vsyncNanos] is its vsync — when it became visible to the user;
     * [frameDispatchNanos] is when the rendering engine first dispatched work to build it (its vsync less
     * the full time it took to produce — far before the vsync for a stuck frame); and [jankNanos] is how
     * far it overran its budget.
     */
    fun onFrame(vsyncNanos: Long, frameDispatchNanos: Long, jankNanos: Long)

    /**
     * A new screen began (Activity resumed).
     */
    fun onScreenStart()

    /**
     * The current screen was paused (Activity paused) — e.g. the app was backgrounded.
     */
    fun onScreenStop()

    /**
     * A user interaction began (touch down). The [eventTime] is the
     * [android.os.SystemClock.uptimeMillis] that the raw touch event occurred, ahead of whenever this
     * callback itself happens to run.
     */
    fun onInteractionStart(eventTime: Long)

    /**
     * A user interaction moved (touch move) — a liveness signal while a finger is down.
     */
    fun onInteractionMove()

    /**
     * A user interaction ended (touch up/cancel).
     */
    fun onInteractionEnd()

    /**
     * A committed tap (touch up) — the action that may trigger a navigation, and so the start of a screen load. The [eventTime] is the
     * [android.os.SystemClock.uptimeMillis] that the event occurred.
     */
    fun onTap(eventTime: Long)

    /**
     * A navigation began towards [screenName] (if known) — confirms a possible screen load.
     */
    fun onNavigationStart(screenName: String?)

    /**
     * A navigation reached [screenName] (if known) — arms the settle that ends the screen load.
     */
    fun onNavigationEnd(screenName: String?)

    /**
     * The window gained input focus: the open transition finished, extending an in-flight screen load past
     * the animation tail.
     */
    fun onWindowFocused()

    /**
     * The app entered the background — interrupts an in-flight screen load.
     */
    fun onAppBackgrounded()
}

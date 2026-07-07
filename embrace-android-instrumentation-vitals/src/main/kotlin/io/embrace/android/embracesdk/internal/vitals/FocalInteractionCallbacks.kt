package io.embrace.android.embracesdk.internal.vitals

/**
 * Receives the raw signals captured by the Vitals instrumentation. Implemented by the smoothness tracker.
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
     * A user interaction began (touch down).
     */
    fun onInteractionStart()

    /**
     * A user interaction moved (touch move) — a liveness signal while a finger is down.
     */
    fun onInteractionMove()

    /**
     * A user interaction ended (touch up/cancel).
     */
    fun onInteractionEnd()
}

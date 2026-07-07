package io.embrace.android.embracesdk.internal.arch.navigation

/**
 * Receives the navigation signals that gate the screen-load vital. A navigation start confirms that a
 * possible screen load (begun by a user interaction) is a real navigation; a navigation end marks the
 * destination as reached and arms the settle that ends the screen load.
 *
 * Implemented by the vitals instrumentation and driven by navigation sources (e.g. the navigation
 * instrumentation) via [NavigationTrackingService]. The screen name uses last-writer-wins so that a
 * navigation that immediately redirects resolves to the final destination.
 */
interface ScreenLoadVitalListener {

    /**
     * A navigation has begun towards [screenName] (if known), detected at [timestampMs].
     *
     * [timestampMs] is the canonical event time, carried for consistency with the other navigation
     * listeners; the current implementation does not yet consume it (screen-load timing is anchored on the
     * initial interaction and the destination settle, measured on the vitals thread).
     */
    fun onNavigationStart(screenName: String, timestampMs: Long)

    /**
     * A navigation has reached [screenName], detected at [timestampMs].
     *
     * See [onNavigationStart] regarding [timestampMs].
     */
    fun onNavigationEnd(screenName: String, timestampMs: Long)
}

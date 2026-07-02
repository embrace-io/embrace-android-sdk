package io.embrace.android.embracesdk.internal.vitals

/**
 * Maps Activity lifecycle to the screen-load navigation signals: a newly *created* Activity is a forward navigation start, a resumed
 * Activity is its end.
 */
internal class ActivityNavigationSource(
    private val callbacks: FocalInteractionCallbacks,
) {

    // the first creation is the cold start (measured as startup), not an in-app navigation
    private var skipNextCreate = true

    /**
     * A newly created Activity is a forward navigation start. [recreated] (a config change or process-death restore) rebuilds the same
     * screen,
     */
    fun onActivityCreated(screenName: String, recreated: Boolean) {
        val skip = skipNextCreate || recreated
        skipNextCreate = false

        if (!skip) {
            callbacks.onNavigationStart(screenName)
        }
    }

    fun onActivityResumed(screenName: String) {
        callbacks.onNavigationEnd(screenName)
    }
}

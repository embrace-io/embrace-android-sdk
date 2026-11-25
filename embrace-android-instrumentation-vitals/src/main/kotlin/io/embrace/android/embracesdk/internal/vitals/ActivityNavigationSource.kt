package io.embrace.android.embracesdk.internal.vitals

import io.embrace.android.embracesdk.internal.arch.vitals.FocalMomentType
import io.embrace.android.embracesdk.internal.utils.event.EventBus

/**
 * Maps Activity lifecycle to the screen-load navigation signals: a newly *created* Activity is a forward navigation start, a resumed
 * Activity is its end. Each forward navigation also triggers a [FocalMomentType.SCREEN_LOAD] focal moment.
 */
internal class ActivityNavigationSource(
    private val callbacks: FocalInteractionCallbacks,
    private val eventBus: EventBus,
) {

    // the first creation is the cold start (measured as startup), not an in-app navigation
    private var skipNextCreate = true

    /**
     * A newly created Activity is a forward navigation start. [recreated] (a config change or process-death restore) rebuilds the same
     * screen,
     */
    fun onActivityCreated(screenName: String, recreated: Boolean, eventTime: Long) {
        val skip = skipNextCreate || recreated
        skipNextCreate = false

        if (!skip) {
            callbacks.onNavigationStart(screenName, eventTime)
            eventBus.emit(FocalMomentType.KEY, FocalMomentType.SCREEN_LOAD)
        }
    }

    fun onActivityResumed(screenName: String, eventTime: Long) {
        callbacks.onNavigationEnd(screenName, eventTime)
    }
}

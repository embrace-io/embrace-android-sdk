package io.embrace.android.embracesdk.internal.arch.vitals

import io.embrace.android.embracesdk.internal.utils.event.EventKey

/**
 * The kinds of focal moment: a span of time in which the user is watching the app respond.
 */
enum class FocalMomentType {
    /**
     * The user is interacting with the screen they are on.
     */
    INTERACTION,

    /**
     * A new screen is loading.
     */
    SCREEN_LOAD,
    ;

    companion object {
        /**
         * Emitting a type on this key starts a focal moment of that type, or folds into the one already open: a [SCREEN_LOAD] turns an
         * open [INTERACTION] into a screen load, or interrupts an open screen load and starts another. Any instrumentation may emit on
         * it, from any thread.
         */
        val KEY = EventKey<FocalMomentType>()
    }
}

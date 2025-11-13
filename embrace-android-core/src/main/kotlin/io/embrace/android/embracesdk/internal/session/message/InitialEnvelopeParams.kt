package io.embrace.android.embracesdk.internal.session.message

import io.embrace.android.embracesdk.internal.session.LifeEventType
import io.embrace.android.embracesdk.internal.session.lifecycle.AppState
import io.embrace.android.embracesdk.internal.store.Ordinal
import io.embrace.android.embracesdk.internal.store.OrdinalStore

/**
 * Holds the parameters & logic needed to create an initial session object.
 */
class InitialEnvelopeParams(
    val coldStart: Boolean,
    val startType: LifeEventType,
    val startTime: Long,
    val appState: AppState,
) {

    fun getSessionNumber(store: OrdinalStore): Int = when (appState) {
        AppState.FOREGROUND -> store.incrementAndGet(Ordinal.SESSION)
        AppState.BACKGROUND -> store.incrementAndGet(Ordinal.BACKGROUND_ACTIVITY)
    }
}

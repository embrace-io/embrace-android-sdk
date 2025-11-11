package io.embrace.android.embracesdk.internal.session.message

import io.embrace.android.embracesdk.internal.payload.ApplicationState
import io.embrace.android.embracesdk.internal.payload.LifeEventType
import io.embrace.android.embracesdk.internal.store.Ordinal
import io.embrace.android.embracesdk.internal.store.OrdinalStore

/**
 * Holds the parameters & logic needed to create an initial session object.
 */
class InitialEnvelopeParams(
    val coldStart: Boolean,
    val startType: LifeEventType,
    val startTime: Long,
    val appState: ApplicationState,
) {

    fun getSessionNumber(store: OrdinalStore): Int = when (appState) {
        ApplicationState.FOREGROUND -> store.incrementAndGet(Ordinal.SESSION)
        ApplicationState.BACKGROUND -> store.incrementAndGet(Ordinal.BACKGROUND_ACTIVITY)
    }
}

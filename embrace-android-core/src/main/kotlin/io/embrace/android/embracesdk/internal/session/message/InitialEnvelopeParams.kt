package io.embrace.android.embracesdk.internal.session.message

import io.embrace.android.embracesdk.internal.payload.ApplicationState
import io.embrace.android.embracesdk.internal.payload.LifeEventType
import io.embrace.android.embracesdk.internal.prefs.PreferencesService

/**
 * Holds the parameters & logic needed to create an initial session object.
 */
public class InitialEnvelopeParams(
    public val coldStart: Boolean,
    public val startType: LifeEventType,
    public val startTime: Long,
    public val appState: ApplicationState
) {

    public fun getSessionNumber(service: PreferencesService): Int = when (appState) {
        ApplicationState.FOREGROUND -> service.incrementAndGetSessionNumber()
        ApplicationState.BACKGROUND -> service.incrementAndGetBackgroundActivityNumber()
    }
}

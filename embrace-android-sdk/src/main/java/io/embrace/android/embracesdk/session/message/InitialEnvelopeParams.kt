package io.embrace.android.embracesdk.session.message

import io.embrace.android.embracesdk.payload.ApplicationState
import io.embrace.android.embracesdk.payload.LifeEventType
import io.embrace.android.embracesdk.prefs.PreferencesService
import io.embrace.android.embracesdk.session.properties.SessionPropertiesService

/**
 * Holds the parameters & logic needed to create an initial session object.
 */
internal sealed class InitialEnvelopeParams(
    val coldStart: Boolean,
    val startType: LifeEventType,
    val startTime: Long
) {
    abstract val appState: ApplicationState
    abstract fun getSessionNumber(service: PreferencesService): Int
    abstract fun getProperties(service: SessionPropertiesService): Map<String, String>?

    /**
     * Initial parameters required to create a session object.
     */
    internal class SessionParams(
        coldStart: Boolean,
        startType: LifeEventType,
        startTime: Long
    ) : InitialEnvelopeParams(coldStart, startType, startTime) {

        override val appState = ApplicationState.FOREGROUND
        override fun getSessionNumber(service: PreferencesService): Int =
            service.incrementAndGetSessionNumber()

        override fun getProperties(service: SessionPropertiesService): Map<String, String> =
            service.getProperties()
    }

    /**
     * Initial parameters required to create a background activity object.
     */
    internal class BackgroundActivityParams(
        coldStart: Boolean,
        startType: LifeEventType,
        startTime: Long
    ) : InitialEnvelopeParams(coldStart, startType, startTime) {

        override val appState = ApplicationState.BACKGROUND
        override fun getSessionNumber(service: PreferencesService): Int =
            service.incrementAndGetBackgroundActivityNumber()

        override fun getProperties(service: SessionPropertiesService): Map<String, String>? = null
    }
}

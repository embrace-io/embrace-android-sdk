package io.embrace.android.embracesdk.internal.session.message

import io.embrace.android.embracesdk.internal.arch.state.AppState
import io.embrace.android.embracesdk.internal.session.LifeEventType

/**
 * Holds the parameters & logic needed to create an initial session object.
 */
class InitialEnvelopeParams(
    val coldStart: Boolean,
    val startType: LifeEventType,
    val startTime: Long,
    val appState: AppState,
)

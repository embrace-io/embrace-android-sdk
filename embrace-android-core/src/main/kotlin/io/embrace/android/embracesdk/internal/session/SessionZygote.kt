package io.embrace.android.embracesdk.internal.session

import io.embrace.android.embracesdk.internal.payload.ApplicationState
import io.embrace.android.embracesdk.internal.payload.LifeEventType

/**
 * A precursor object that holds state associated with a newly started session.
 */
data class SessionZygote(

    /**
     * A unique ID which identifies the session.
     */
    val sessionId: String,

    /**
     * The time that the session started.
     */
    val startTime: Long,

    /**
     * The ordinal of the session, starting from 1.
     */
    val number: Int,

    /**
     * Application state for this session (foreground or background)
     */
    val appState: ApplicationState,

    /**
     * Whether the session is a cold start or not.
     */
    val isColdStart: Boolean,

    /**
     * The type of start event that triggered this session.
     */
    val startType: LifeEventType,
)

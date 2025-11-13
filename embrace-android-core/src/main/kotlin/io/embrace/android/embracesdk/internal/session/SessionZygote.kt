package io.embrace.android.embracesdk.internal.session

import io.embrace.android.embracesdk.internal.session.lifecycle.AppState

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
     * Process state for this session (foreground or background)
     */
    val appState: AppState,

    /**
     * Whether the session is a cold start or not.
     */
    val isColdStart: Boolean,

    /**
     * The type of start event that triggered this session.
     */
    val startType: LifeEventType,
)

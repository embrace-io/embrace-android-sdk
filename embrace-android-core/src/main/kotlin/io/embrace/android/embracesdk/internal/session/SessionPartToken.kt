package io.embrace.android.embracesdk.internal.session

import io.embrace.android.embracesdk.internal.arch.state.AppState

/**
 * An object that holds immutable state associated with a started session.
 */
data class SessionPartToken(

    /**
     * A unique ID which identifies the session part.
     */
    val sessionPartId: String,

    /**
     * The ID of the user session this part is associated with.
     * Stored as part of this token so it can wholly represent the SDK's current session state by itself.
     */
    val userSessionId: String,

    /**
     * The time that the session started.
     */
    val startTime: Long,

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

    /**
     * Monotonic index of this part of the user session.
     */
    val sessionPartNumber: Int = 0,
)

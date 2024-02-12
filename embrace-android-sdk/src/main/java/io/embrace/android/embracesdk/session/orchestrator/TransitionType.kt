package io.embrace.android.embracesdk.session.orchestrator

import io.embrace.android.embracesdk.session.lifecycle.ProcessState

/**
 * Defines all the possible state transitions that can happen with a session.
 */
internal enum class TransitionType {

    /**
     * The initial state transition. This will be from undefined to either foreground/background.
     */
    INITIAL,

    /**
     * Manual end request - only respected for foreground sessions.
     */
    END_MANUAL,

    /**
     * When lifecycle callbacks detect the process has entered the foreground.
     */
    ON_FOREGROUND,

    /**
     * When lifecycle callbacks detect the process has entered the background.
     */
    ON_BACKGROUND,

    /**
     * When a JVM unhandled exception is detected.
     */
    CRASH;

    /**
     * Returns the end state of the session based on the current state and the transition type.
     */
    fun endState(currentState: ProcessState): ProcessState = when (this) {
        ON_FOREGROUND -> ProcessState.FOREGROUND
        ON_BACKGROUND -> ProcessState.BACKGROUND
        else -> currentState
    }
}

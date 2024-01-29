package io.embrace.android.embracesdk.session.orchestrator

import io.embrace.android.embracesdk.session.lifecycle.ProcessState

internal enum class TransitionType {
    INITIAL, END_MANUAL, ON_FOREGROUND, ON_BACKGROUND, CRASH;

    fun endState(currentState: ProcessState): ProcessState = when (this) {
        ON_FOREGROUND -> ProcessState.FOREGROUND
        ON_BACKGROUND -> ProcessState.BACKGROUND
        else -> currentState
    }
}

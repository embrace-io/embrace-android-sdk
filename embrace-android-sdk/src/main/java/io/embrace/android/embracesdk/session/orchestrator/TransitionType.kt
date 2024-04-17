package io.embrace.android.embracesdk.session.orchestrator

import io.embrace.android.embracesdk.payload.Session
import io.embrace.android.embracesdk.session.lifecycle.ProcessState

internal enum class TransitionType {
    INITIAL, END_MANUAL, ON_FOREGROUND, ON_BACKGROUND, CRASH;

    fun endState(currentState: ProcessState): ProcessState = when (this) {
        ON_FOREGROUND -> ProcessState.FOREGROUND
        ON_BACKGROUND -> ProcessState.BACKGROUND
        else -> currentState
    }

    fun lifeEventType(currentState: ProcessState): Session.LifeEventType = when (this) {
        END_MANUAL -> when (currentState) {
            ProcessState.FOREGROUND -> Session.LifeEventType.MANUAL
            else -> Session.LifeEventType.BKGND_MANUAL
        }

        else -> when (currentState) {
            ProcessState.FOREGROUND -> Session.LifeEventType.STATE
            else -> Session.LifeEventType.BKGND_STATE
        }
    }
}

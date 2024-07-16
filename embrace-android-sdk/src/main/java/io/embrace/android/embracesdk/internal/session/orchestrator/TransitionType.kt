package io.embrace.android.embracesdk.internal.session.orchestrator

import io.embrace.android.embracesdk.internal.payload.LifeEventType
import io.embrace.android.embracesdk.internal.session.lifecycle.ProcessState

internal enum class TransitionType {
    INITIAL, END_MANUAL, ON_FOREGROUND, ON_BACKGROUND, CRASH;

    fun endState(currentState: ProcessState): ProcessState = when (this) {
        ON_FOREGROUND -> ProcessState.FOREGROUND
        ON_BACKGROUND -> ProcessState.BACKGROUND
        else -> currentState
    }

    fun lifeEventType(currentState: ProcessState): LifeEventType = when (this) {
        END_MANUAL -> when (currentState) {
            ProcessState.FOREGROUND -> LifeEventType.MANUAL
            else -> LifeEventType.BKGND_MANUAL
        }

        else -> when (currentState) {
            ProcessState.FOREGROUND -> LifeEventType.STATE
            else -> LifeEventType.BKGND_STATE
        }
    }
}

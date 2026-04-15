package io.embrace.android.embracesdk.internal.session.orchestrator

import io.embrace.android.embracesdk.internal.arch.state.AppState
import io.embrace.android.embracesdk.internal.session.LifeEventType

enum class TransitionType {
    INITIAL, END_MANUAL, ON_FOREGROUND, ON_BACKGROUND, CRASH, INACTIVITY_TIMEOUT, INACTIVITY_FOREGROUND, MAX_DURATION;

    fun endState(currentState: AppState): AppState = when (this) {
        ON_FOREGROUND, INACTIVITY_FOREGROUND -> AppState.FOREGROUND
        ON_BACKGROUND, INACTIVITY_TIMEOUT -> AppState.BACKGROUND
        else -> currentState
    }

    fun lifeEventType(currentState: AppState): LifeEventType = when (this) {
        END_MANUAL -> when (currentState) {
            AppState.FOREGROUND -> LifeEventType.MANUAL
            else -> LifeEventType.BKGND_MANUAL
        }

        else -> when (currentState) {
            AppState.FOREGROUND -> LifeEventType.STATE
            else -> LifeEventType.BKGND_STATE
        }
    }
}

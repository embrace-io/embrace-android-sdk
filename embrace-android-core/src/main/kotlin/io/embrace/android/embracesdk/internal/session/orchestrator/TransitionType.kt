package io.embrace.android.embracesdk.internal.session.orchestrator

import io.embrace.android.embracesdk.internal.session.LifeEventType
import io.embrace.android.embracesdk.internal.session.lifecycle.AppState

enum class TransitionType {
    INITIAL, END_MANUAL, ON_FOREGROUND, ON_BACKGROUND, CRASH;

    fun endState(currentState: AppState): AppState = when (this) {
        ON_FOREGROUND -> AppState.FOREGROUND
        ON_BACKGROUND -> AppState.BACKGROUND
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

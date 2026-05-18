@file:OptIn(ExperimentalSemconv::class)

package io.embrace.android.embracesdk.internal.session.orchestrator

import io.embrace.android.embracesdk.internal.arch.state.AppState
import io.embrace.android.embracesdk.internal.session.LifeEventType
import io.embrace.android.embracesdk.semconv.EmbSessionAttributes
import io.embrace.android.embracesdk.semconv.EmbSessionAttributes.EmbUserSessionTerminationReasonValues
import io.embrace.android.embracesdk.semconv.ExperimentalSemconv

enum class TransitionType {
    INITIAL, END_MANUAL, ON_FOREGROUND, ON_BACKGROUND, CRASH, INACTIVITY_TIMEOUT, INACTIVITY_FOREGROUND, MAX_DURATION;

    val endAttributes: Map<String, String> by lazy {
        when (this) {
            END_MANUAL ->
                mapOf(
                    EmbSessionAttributes.EMB_IS_FINAL_SESSION_PART to "1",
                    EmbSessionAttributes.EMB_USER_SESSION_TERMINATION_REASON to EmbUserSessionTerminationReasonValues.MANUAL,
                )

            INACTIVITY_TIMEOUT, INACTIVITY_FOREGROUND ->
                mapOf(
                    EmbSessionAttributes.EMB_IS_FINAL_SESSION_PART to "1",
                    EmbSessionAttributes.EMB_USER_SESSION_TERMINATION_REASON to EmbUserSessionTerminationReasonValues.INACTIVITY,
                )

            MAX_DURATION ->
                mapOf(
                    EmbSessionAttributes.EMB_IS_FINAL_SESSION_PART to "1",
                    EmbSessionAttributes.EMB_USER_SESSION_TERMINATION_REASON to EmbUserSessionTerminationReasonValues.MAX_DURATION_REACHED,
                )

            else -> emptyMap()
        }
    }

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

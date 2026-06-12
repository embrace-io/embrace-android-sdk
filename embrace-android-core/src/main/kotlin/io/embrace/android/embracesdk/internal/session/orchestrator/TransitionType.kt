@file:OptIn(ExperimentalSemconv::class)

package io.embrace.android.embracesdk.internal.session.orchestrator

import io.embrace.android.embracesdk.internal.arch.state.AppState
import io.embrace.android.embracesdk.internal.session.LifeEventType
import io.embrace.android.embracesdk.semconv.EmbSessionAttributes
import io.embrace.android.embracesdk.semconv.EmbSessionAttributes.EmbUserSessionTerminationReasonValues
import io.embrace.android.embracesdk.semconv.ExperimentalSemconv

/**
 * The type of transition between session parts.
 */
enum class TransitionType(
    /**
     * The reason the active user session is terminated as a result of this transition, or null if this transition doesn't always terminate
     * the active user session.
     */
    val userSessionTerminationReason: String? = null,

    /**
     * The [AppState] the SDK will be in once this transition completes, or null if the transition doesn't change the [AppState]
     */
    private val nextAppState: AppState? = null,
) {
    INITIAL,
    END_MANUAL(
        userSessionTerminationReason = EmbUserSessionTerminationReasonValues.MANUAL,
    ),
    ON_FOREGROUND(
        nextAppState = AppState.FOREGROUND,
    ),
    ON_BACKGROUND(
        nextAppState = AppState.BACKGROUND,
    ),
    CRASH,
    INACTIVITY_TIMEOUT(
        userSessionTerminationReason = EmbUserSessionTerminationReasonValues.INACTIVITY,
        nextAppState = AppState.BACKGROUND,
    ),
    INACTIVITY_FOREGROUND(
        userSessionTerminationReason = EmbUserSessionTerminationReasonValues.INACTIVITY,
        nextAppState = AppState.FOREGROUND,
    ),
    MAX_DURATION(
        userSessionTerminationReason = EmbUserSessionTerminationReasonValues.MAX_DURATION_REACHED,
    ),
    BACKGROUND_ONLY_SESSION_END(
        userSessionTerminationReason = EmbUserSessionTerminationReasonValues.END_BACKGROUND_ONLY_USER_SESSION,
        nextAppState = AppState.FOREGROUND,
    );

    /**
     * True if this session part transition always terminates the active user session
     */
    val endsUserSession: Boolean
        get() = userSessionTerminationReason != null

    /**
     * Attributes added to the session parts span just before it ends.
     */
    val endAttributes: Map<String, String> by lazy {
        userSessionTerminationReason?.let { reason ->
            mapOf(
                EmbSessionAttributes.EMB_IS_FINAL_SESSION_PART to "1",
                EmbSessionAttributes.EMB_USER_SESSION_TERMINATION_REASON to reason,
            )
        } ?: emptyMap()
    }

    /**
     * The [AppState] the SDK will be in after transitioning into this stage, given the current state.
     */
    fun postTransitionEndState(currentState: AppState): AppState = nextAppState ?: currentState

    /**
     * The [LifeEventType] that triggered this transition
     */
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

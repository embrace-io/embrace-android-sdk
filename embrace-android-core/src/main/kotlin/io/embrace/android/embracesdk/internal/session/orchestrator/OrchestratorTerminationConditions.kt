package io.embrace.android.embracesdk.internal.session.orchestrator

import io.embrace.android.embracesdk.internal.arch.state.AppState
import io.embrace.android.embracesdk.internal.clock.Clock
import io.embrace.android.embracesdk.internal.config.ConfigService

internal fun shouldEndManualSession(
    configService: ConfigService,
    clock: Clock,
    activeSessionStartTime: Long?,
    state: AppState,
): Boolean {
    if (state == AppState.BACKGROUND || configService.sessionBehavior.isSessionControlEnabled() || activeSessionStartTime == null) {
        return true
    }

    val delta = clock.now() - activeSessionStartTime
    return delta < configService.sessionBehavior.getMinSessionDurationMs()
}

internal fun shouldRunOnBackground(
    state: AppState,
): Boolean {
    return state == AppState.BACKGROUND
}

internal fun shouldRunOnForeground(
    state: AppState,
): Boolean {
    return state == AppState.FOREGROUND
}

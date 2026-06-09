package io.embrace.android.embracesdk.internal.session.orchestrator

import io.embrace.android.embracesdk.internal.arch.state.AppState
import io.embrace.android.embracesdk.internal.clock.Clock
import io.embrace.android.embracesdk.internal.config.ConfigService

internal fun shouldEndManualSession(
    configService: ConfigService,
    clock: Clock,
    userSessionStartTimeMs: Long?,
    lastManualEndMs: Long?,
): Boolean {
    if (configService.sessionBehavior.isSessionControlEnabled() || userSessionStartTimeMs == null) {
        return true
    }
    if (lastManualEndMs == null) {
        return false
    }
    val delta = clock.now() - lastManualEndMs
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

package io.embrace.android.embracesdk.internal.session.orchestrator

import io.embrace.android.embracesdk.internal.arch.state.AppState
import io.embrace.android.embracesdk.internal.clock.Clock
import io.embrace.android.embracesdk.internal.config.ConfigService
import io.embrace.android.embracesdk.internal.session.SessionZygote

/**
 * The minimum threshold for how long a session must last. This prevents unintentional
 * instrumentation from creating new sessions in a hot loop & therefore spamming
 * our servers.
 */
private const val MIN_SESSION_MS = 5000L

internal fun shouldEndManualSession(
    configService: ConfigService,
    clock: Clock,
    activeSession: SessionZygote?,
    state: AppState,
): Boolean {
    if (state == AppState.BACKGROUND || configService.sessionBehavior.isSessionControlEnabled()) {
        return true
    }
    val initial = activeSession ?: return true
    val startTime = initial.startTime
    val delta = clock.now() - startTime
    return delta < MIN_SESSION_MS
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

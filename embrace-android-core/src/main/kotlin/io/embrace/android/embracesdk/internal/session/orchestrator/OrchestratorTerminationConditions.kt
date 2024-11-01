package io.embrace.android.embracesdk.internal.session.orchestrator

import io.embrace.android.embracesdk.internal.clock.Clock
import io.embrace.android.embracesdk.internal.config.ConfigService
import io.embrace.android.embracesdk.internal.session.SessionZygote
import io.embrace.android.embracesdk.internal.session.lifecycle.ProcessState

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
    state: ProcessState,
): Boolean {
    if (state == ProcessState.BACKGROUND || configService.sessionBehavior.isSessionControlEnabled()) {
        return true
    }
    val initial = activeSession ?: return true
    val startTime = initial.startTime
    val delta = clock.now() - startTime
    return delta < MIN_SESSION_MS
}

internal fun shouldRunOnBackground(
    state: ProcessState,
): Boolean {
    return state == ProcessState.BACKGROUND
}

internal fun shouldRunOnForeground(
    state: ProcessState,
): Boolean {
    return state == ProcessState.FOREGROUND
}

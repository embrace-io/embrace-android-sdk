package io.embrace.android.embracesdk.internal.session.orchestrator

import io.embrace.android.embracesdk.internal.clock.Clock
import io.embrace.android.embracesdk.internal.config.ConfigService
import io.embrace.android.embracesdk.internal.logging.EmbLogger
import io.embrace.android.embracesdk.internal.payload.SessionZygote
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
    logger: EmbLogger
): Boolean {
    if (state == ProcessState.BACKGROUND) {
        logger.logWarning("Cannot manually end session while in background.")
        return false
    }
    if (!configService.sessionBehavior.isSessionControlEnabled()) {
        logger.logWarning("Cannot manually end session while session control is disabled.")
        return false
    }
    val initial = activeSession ?: return false
    val startTime = initial.startTime
    val delta = clock.now() - startTime
    if (delta < MIN_SESSION_MS) {
        logger.logWarning(
            "Cannot manually end session while session is <5s long." +
                "This protects against instrumentation unintentionally creating too" +
                "many sessions"
        )
        return false
    }
    return true
}

internal fun shouldRunOnBackground(
    state: ProcessState,
    logger: EmbLogger
): Boolean {
    return if (state == ProcessState.BACKGROUND) {
        logger.logWarning("Detected unbalanced call to onBackground. Ignoring..")
        true
    } else {
        false
    }
}

internal fun shouldRunOnForeground(
    state: ProcessState,
    logger: EmbLogger
): Boolean {
    return if (state == ProcessState.FOREGROUND) {
        logger.logWarning("Detected unbalanced call to onForeground. Ignoring..")
        true
    } else {
        false
    }
}

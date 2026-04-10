@file:OptIn(ExperimentalSemconv::class)

package io.embrace.android.embracesdk.internal.session

import io.embrace.android.embracesdk.internal.clock.Clock
import io.embrace.android.embracesdk.internal.config.ConfigService
import io.embrace.android.embracesdk.internal.session.UserSessionState.NO_ACTIVE_USER_SESSION
import io.embrace.android.embracesdk.internal.session.UserSessionState.USER_SESSION_ACTIVE
import io.embrace.android.embracesdk.internal.session.UserSessionState.USER_SESSION_TERMINATED
import io.embrace.android.embracesdk.internal.utils.Uuid
import io.embrace.android.embracesdk.semconv.ExperimentalSemconv
import java.util.concurrent.atomic.AtomicLong

internal class UserSessionOrchestratorImpl(
    private val clock: Clock,
    configService: ConfigService,
) : UserSessionOrchestrator {

    private val lock = Any()

    // TODO: future: support restoring user sessions that contain crashes in the middle, and setting initial state appropriately.
    @Volatile
    private var state: UserSessionState = NO_ACTIVE_USER_SESSION

    @Volatile
    private var metadata: UserSessionMetadata? = null

    // TODO: future: persist the incremented ordinal
    private val sessionCounter = AtomicLong(0L)

    private val maxDurationMs: Long = configService.sessionBehavior.getMaxSessionDurationMs()
    private val inactivityTimeoutMs: Long = configService.sessionBehavior.getSessionInactivityTimeoutMs()

    override fun onNewSessionPart() {
        synchronized(lock) {
            if (state == USER_SESSION_ACTIVE) {
                val current = checkNotNull(metadata)
                if (clock.now() - current.startTimeMs >= maxDurationMs) {
                    terminateSession()
                    startNewSession()
                }
            } else {
                startNewSession()
            }
        }
    }

    // TODO: future: determine behavior when manual end called and app is in background.
    override fun onManualEnd() {
        synchronized(lock) {
            if (state == USER_SESSION_ACTIVE) {
                terminateSession()
            }
            startNewSession()
        }
    }

    override fun currentUserSession(): UserSessionMetadata? = metadata

    /**
     * Starts a new user session.
     */
    private fun startNewSession() {
        metadata = UserSessionMetadata(
            startTimeMs = clock.now(),
            userSessionId = Uuid.getEmbUuid(),
            userSessionNumber = sessionCounter.incrementAndGet(),
            maxDurationMins = maxDurationMs / 60_000L,
            inactivityTimeoutMins = inactivityTimeoutMs / 60_000L,
        )
        state = USER_SESSION_ACTIVE
    }

    /**
     * Terminates the active user session.
     */
    private fun terminateSession() {
        metadata = null
        state = USER_SESSION_TERMINATED
    }
}

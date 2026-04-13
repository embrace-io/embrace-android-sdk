@file:OptIn(ExperimentalSemconv::class)

package io.embrace.android.embracesdk.internal.session

import io.embrace.android.embracesdk.internal.clock.Clock
import io.embrace.android.embracesdk.internal.config.ConfigService
import io.embrace.android.embracesdk.internal.logging.InternalErrorType
import io.embrace.android.embracesdk.internal.logging.InternalLogger
import io.embrace.android.embracesdk.internal.store.Ordinal
import io.embrace.android.embracesdk.internal.store.OrdinalStore
import io.embrace.android.embracesdk.internal.utils.Uuid
import io.embrace.android.embracesdk.semconv.ExperimentalSemconv
import java.util.concurrent.CopyOnWriteArrayList

internal class UserSessionOrchestratorImpl(
    private val clock: Clock,
    configService: ConfigService,
    private val ordinalStore: OrdinalStore,
    private val metadataStore: UserSessionMetadataStore,
    private val logger: InternalLogger,
) : UserSessionOrchestrator {

    private val lock = Any()
    private val listeners = CopyOnWriteArrayList<UserSessionListener>()

    @Volatile
    private var state: UserSessionState = UserSessionState.Initializing

    private val maxDurationMs: Long = configService.sessionBehavior.getMaxSessionDurationMs()
    private val inactivityTimeoutMs: Long = configService.sessionBehavior.getSessionInactivityTimeoutMs()

    init {
        synchronized(lock) {
            val stored = metadataStore.load()
            state = when {
                stored != null && !isOverMaxDurationLimit(stored) -> UserSessionState.Active(stored)
                else -> UserSessionState.NoActiveSession
            }
        }
    }

    override fun onNewSessionPart() {
        synchronized(lock) {
            val current = state
            if (current is UserSessionState.Active) {
                if (isOverMaxDurationLimit(current.metadata)) {
                    terminateSession()
                    startNewUserSession()
                }
            } else {
                startNewUserSession()
            }
        }
    }

    private fun isOverMaxDurationLimit(metadata: UserSessionMetadata): Boolean {
        return clock.now() - metadata.startTimeMs >= maxDurationMs
    }

    // TODO: future: determine behavior when manual end called and app is in background.
    override fun onManualEnd() {
        synchronized(lock) {
            if (state is UserSessionState.Active) {
                terminateSession()
            }
            startNewUserSession()
        }
    }

    override fun currentUserSession(): UserSessionMetadata? =
        (state as? UserSessionState.Active)?.metadata

    override fun addListener(listener: UserSessionListener) {
        listeners.add(listener)
    }

    /**
     * Starts a new user session.
     */
    private fun startNewUserSession() {
        val newMetadata = UserSessionMetadata(
            startTimeMs = clock.now(),
            userSessionId = Uuid.getEmbUuid(),
            userSessionNumber = ordinalStore.incrementAndGet(Ordinal.USER_SESSION).toLong(),
            maxDurationMins = maxDurationMs / 60_000L,
            inactivityTimeoutMins = inactivityTimeoutMs / 60_000L,
        )
        metadataStore.save(newMetadata)
        state = UserSessionState.Active(newMetadata)
        listeners.forEach { listener ->
            try {
                listener.onNewUserSession()
            } catch (ex: Exception) {
                logger.trackInternalError(InternalErrorType.USER_SESSION_CALLBACK_FAIL, ex)
            }
        }
    }

    /**
     * Terminates the active user session.
     */
    private fun terminateSession() {
        metadataStore.clear()
        state = UserSessionState.Terminated
    }
}

package io.embrace.android.embracesdk.internal.session.id

import io.embrace.android.embracesdk.internal.session.orchestrator.SessionOrchestrator

internal class SessionIdsProviderImpl(
    private val sessionOrchestratorProvider: () -> SessionOrchestrator?,
    private val sessionPartTracker: SessionPartTracker,
) : SessionIdsProvider {

    /**
     * Memoized [SessionIdsSnapshot] returned by [getActiveSessionIds], reused as long as its IDs still match the active
     * session part, so that repeated calls during the same session part return the same instance rather than each caller
     * allocating its own.
     */
    @Volatile
    private var cachedSessionIdsSnapshot: SessionIdsSnapshot? = null

    override fun getCurrentUserSessionId(): String =
        sessionOrchestratorProvider()?.currentUserSession()?.userSessionId ?: ""

    override fun getCurrentSessionPartId(): String =
        sessionPartTracker.getActiveSessionPart()?.sessionPartId ?: ""

    override fun getActiveSessionIds(): SessionIdsSnapshot {
        val activePart = sessionPartTracker.getActiveSessionPart() ?: return SessionIdsSnapshot(
            userSessionId = sessionOrchestratorProvider()?.currentUserSession()?.userSessionId.orEmpty(),
            sessionPartId = "",
        )

        val snapshot = cachedSessionIdsSnapshot
        if (snapshot != null &&
            snapshot.userSessionId == activePart.userSessionId &&
            snapshot.sessionPartId == activePart.sessionPartId
        ) {
            return snapshot
        }

        return SessionIdsSnapshot(
            userSessionId = activePart.userSessionId,
            sessionPartId = activePart.sessionPartId,
        ).also { cachedSessionIdsSnapshot = it }
    }
}

package io.embrace.android.embracesdk.internal.session.id

import io.embrace.android.embracesdk.internal.session.orchestrator.SessionOrchestrator

internal class SessionIdProviderImpl(
    private val sessionOrchestratorProvider: () -> SessionOrchestrator?,
    private val sessionPartTracker: SessionPartTracker,
) : SessionIdProvider, ActiveSessionIdsProvider {

    override fun getCurrentUserSessionId(): String =
        sessionOrchestratorProvider()?.currentUserSession()?.userSessionId ?: ""

    override fun getCurrentSessionPartId(): String =
        sessionPartTracker.getActiveSessionPart()?.sessionPartId ?: ""

    override fun getActiveSessionIds(): SessionIdsSnapshot =
        SessionIdsSnapshot(
            userSessionId = getCurrentUserSessionId(),
            sessionPartId = getCurrentSessionPartId(),
        )
}

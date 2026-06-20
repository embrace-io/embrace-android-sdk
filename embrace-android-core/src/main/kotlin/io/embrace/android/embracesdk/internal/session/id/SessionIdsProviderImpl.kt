package io.embrace.android.embracesdk.internal.session.id

import io.embrace.android.embracesdk.internal.session.orchestrator.SessionOrchestrator

internal class SessionIdsProviderImpl(
    private val sessionOrchestratorProvider: () -> SessionOrchestrator?,
    private val sessionPartTracker: SessionPartTracker,
) : SessionIdsProvider {

    override fun getCurrentUserSessionId(): String =
        sessionOrchestratorProvider()?.currentUserSession()?.userSessionId ?: ""

    override fun getCurrentSessionPartId(): String =
        sessionPartTracker.getActiveSessionPart()?.sessionPartId ?: ""

    override fun getActiveSessionIds(): SessionIdsSnapshot =
        sessionPartTracker.getActiveSessionPart()?.let {
            SessionIdsSnapshot(
                userSessionId = it.userSessionId,
                sessionPartId = it.sessionPartId,
            )
        } ?: SessionIdsSnapshot(
            userSessionId = sessionOrchestratorProvider()?.currentUserSession()?.userSessionId.orEmpty(),
            sessionPartId = "",
        )
}

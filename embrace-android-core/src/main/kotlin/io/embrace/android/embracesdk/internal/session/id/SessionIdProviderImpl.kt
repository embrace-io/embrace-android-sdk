package io.embrace.android.embracesdk.internal.session.id

import io.embrace.android.embracesdk.internal.session.orchestrator.SessionOrchestrator

internal class SessionIdProviderImpl(
    private val sessionOrchestratorProvider: () -> SessionOrchestrator?,
    private val sessionPartTracker: SessionPartTracker,
) : SessionIdProvider {

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

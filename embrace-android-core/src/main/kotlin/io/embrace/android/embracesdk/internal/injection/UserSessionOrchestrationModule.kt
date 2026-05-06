package io.embrace.android.embracesdk.internal.injection

import io.embrace.android.embracesdk.internal.session.id.SessionIdProvider
import io.embrace.android.embracesdk.internal.session.orchestrator.SessionOrchestrator

/**
 * Contains the orchestrators that manage the session lifecycle.
 */
interface UserSessionOrchestrationModule {
    val sessionOrchestrator: SessionOrchestrator
    val sessionIdProvider: SessionIdProvider
}

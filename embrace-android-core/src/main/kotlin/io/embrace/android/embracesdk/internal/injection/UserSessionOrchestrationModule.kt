package io.embrace.android.embracesdk.internal.injection

import io.embrace.android.embracesdk.internal.session.UserSessionOrchestrator
import io.embrace.android.embracesdk.internal.session.orchestrator.SessionPartOrchestrator

/**
 * Contains the orchestrators that manage the session lifecycle.
 */
interface UserSessionOrchestrationModule {
    val sessionPartOrchestrator: SessionPartOrchestrator
    val userSessionOrchestrator: UserSessionOrchestrator
}

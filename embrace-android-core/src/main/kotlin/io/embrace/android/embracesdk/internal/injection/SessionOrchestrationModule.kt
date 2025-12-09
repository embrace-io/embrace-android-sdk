package io.embrace.android.embracesdk.internal.injection

import io.embrace.android.embracesdk.internal.session.orchestrator.SessionOrchestrator

interface SessionOrchestrationModule {
    val sessionOrchestrator: SessionOrchestrator
}

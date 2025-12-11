package io.embrace.android.embracesdk.fakes

import io.embrace.android.embracesdk.internal.injection.SessionOrchestrationModule
import io.embrace.android.embracesdk.internal.session.orchestrator.SessionOrchestrator

class FakeSessionOrchestrationModule(
    override val sessionOrchestrator: SessionOrchestrator = FakeSessionOrchestrator(),
) : SessionOrchestrationModule

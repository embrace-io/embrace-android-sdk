package io.embrace.android.embracesdk.fakes.injection

import io.embrace.android.embracesdk.fakes.FakeSessionOrchestrator
import io.embrace.android.embracesdk.internal.injection.UserSessionOrchestrationModule
import io.embrace.android.embracesdk.internal.session.orchestrator.SessionOrchestrator

class FakeUserSessionOrchestrationModule(
    override val sessionOrchestrator: SessionOrchestrator = FakeSessionOrchestrator(),
) : UserSessionOrchestrationModule

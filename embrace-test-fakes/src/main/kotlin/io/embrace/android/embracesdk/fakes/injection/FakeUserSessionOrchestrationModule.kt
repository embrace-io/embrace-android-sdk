package io.embrace.android.embracesdk.fakes.injection

import io.embrace.android.embracesdk.fakes.FakeSessionIdProvider
import io.embrace.android.embracesdk.fakes.FakeSessionOrchestrator
import io.embrace.android.embracesdk.internal.injection.UserSessionOrchestrationModule
import io.embrace.android.embracesdk.internal.session.id.SessionIdProvider
import io.embrace.android.embracesdk.internal.session.orchestrator.SessionOrchestrator

class FakeUserSessionOrchestrationModule(
    override val sessionOrchestrator: SessionOrchestrator = FakeSessionOrchestrator(),
    override val sessionIdProvider: SessionIdProvider = FakeSessionIdProvider(),
) : UserSessionOrchestrationModule

package io.embrace.android.embracesdk.fakes.injection

import io.embrace.android.embracesdk.fakes.FakeSessionPartOrchestrator
import io.embrace.android.embracesdk.fakes.FakeUserSessionOrchestrator
import io.embrace.android.embracesdk.internal.injection.UserSessionOrchestrationModule
import io.embrace.android.embracesdk.internal.session.UserSessionOrchestrator
import io.embrace.android.embracesdk.internal.session.orchestrator.SessionPartOrchestrator

class FakeUserSessionOrchestrationModule(
    override val sessionPartOrchestrator: SessionPartOrchestrator = FakeSessionPartOrchestrator(),
    override val userSessionOrchestrator: UserSessionOrchestrator = FakeUserSessionOrchestrator(),
) : UserSessionOrchestrationModule

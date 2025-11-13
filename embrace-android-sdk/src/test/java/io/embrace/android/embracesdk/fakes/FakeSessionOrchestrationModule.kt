package io.embrace.android.embracesdk.fakes

import io.embrace.android.embracesdk.internal.injection.SessionOrchestrationModule
import io.embrace.android.embracesdk.internal.session.MemoryCleanerService
import io.embrace.android.embracesdk.internal.session.message.PayloadFactory
import io.embrace.android.embracesdk.internal.session.orchestrator.SessionOrchestrator

class FakeSessionOrchestrationModule(
    override val payloadFactory: PayloadFactory = FakePayloadFactory(),
    override val sessionOrchestrator: SessionOrchestrator = FakeSessionOrchestrator(),
    override val memoryCleanerService: MemoryCleanerService = FakeMemoryCleanerService(),
) : SessionOrchestrationModule

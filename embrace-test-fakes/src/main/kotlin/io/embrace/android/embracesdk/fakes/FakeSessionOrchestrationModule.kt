package io.embrace.android.embracesdk.fakes

import io.embrace.android.embracesdk.internal.gating.GatingService
import io.embrace.android.embracesdk.internal.injection.SessionOrchestrationModule
import io.embrace.android.embracesdk.internal.session.MemoryCleanerService
import io.embrace.android.embracesdk.internal.session.message.PayloadFactory
import io.embrace.android.embracesdk.internal.session.message.PayloadMessageCollator
import io.embrace.android.embracesdk.internal.session.orchestrator.SessionOrchestrator
import io.embrace.android.embracesdk.internal.session.orchestrator.SessionSpanAttrPopulator

class FakeSessionOrchestrationModule(
    override val payloadFactory: PayloadFactory = FakePayloadFactory(),
    override val sessionOrchestrator: SessionOrchestrator = FakeSessionOrchestrator(),
    override val sessionSpanAttrPopulator: SessionSpanAttrPopulator = FakeSessionSpanAttrPopulator(),
    override val memoryCleanerService: MemoryCleanerService = FakeMemoryCleanerService(),
    override val gatingService: GatingService = FakeGatingService(),
) : SessionOrchestrationModule {

    override val payloadMessageCollator: PayloadMessageCollator
        get() = TODO("Not yet implemented")
}

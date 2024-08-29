package io.embrace.android.embracesdk.internal.injection

import io.embrace.android.embracesdk.internal.gating.GatingService
import io.embrace.android.embracesdk.internal.session.MemoryCleanerService
import io.embrace.android.embracesdk.internal.session.message.PayloadFactory
import io.embrace.android.embracesdk.internal.session.message.PayloadMessageCollator
import io.embrace.android.embracesdk.internal.session.orchestrator.SessionOrchestrator
import io.embrace.android.embracesdk.internal.session.orchestrator.SessionSpanAttrPopulator

public interface SessionOrchestrationModule {
    public val payloadFactory: PayloadFactory
    public val payloadMessageCollator: PayloadMessageCollator
    public val sessionOrchestrator: SessionOrchestrator
    public val sessionSpanAttrPopulator: SessionSpanAttrPopulator
    public val memoryCleanerService: MemoryCleanerService
    public val gatingService: GatingService
}

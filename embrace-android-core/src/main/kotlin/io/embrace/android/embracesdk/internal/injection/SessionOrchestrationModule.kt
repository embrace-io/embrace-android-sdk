package io.embrace.android.embracesdk.internal.injection

import io.embrace.android.embracesdk.internal.gating.GatingService
import io.embrace.android.embracesdk.internal.session.MemoryCleanerService
import io.embrace.android.embracesdk.internal.session.message.PayloadFactory
import io.embrace.android.embracesdk.internal.session.message.PayloadMessageCollator
import io.embrace.android.embracesdk.internal.session.orchestrator.SessionOrchestrator
import io.embrace.android.embracesdk.internal.session.orchestrator.SessionSpanAttrPopulator

interface SessionOrchestrationModule {
    val payloadFactory: PayloadFactory
    val payloadMessageCollator: PayloadMessageCollator
    val sessionOrchestrator: SessionOrchestrator
    val sessionSpanAttrPopulator: SessionSpanAttrPopulator
    val memoryCleanerService: MemoryCleanerService
    val gatingService: GatingService
}

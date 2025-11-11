package io.embrace.android.embracesdk.internal.injection

import io.embrace.android.embracesdk.internal.session.MemoryCleanerService
import io.embrace.android.embracesdk.internal.session.message.PayloadFactory
import io.embrace.android.embracesdk.internal.session.orchestrator.SessionOrchestrator

interface SessionOrchestrationModule {
    val payloadFactory: PayloadFactory
    val sessionOrchestrator: SessionOrchestrator
    val memoryCleanerService: MemoryCleanerService
}

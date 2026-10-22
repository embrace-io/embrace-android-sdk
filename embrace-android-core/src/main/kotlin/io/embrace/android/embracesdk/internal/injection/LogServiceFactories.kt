package io.embrace.android.embracesdk.internal.injection

import io.embrace.android.embracesdk.internal.clock.Clock
import io.embrace.android.embracesdk.internal.envelope.log.LogEnvelopeSource
import io.embrace.android.embracesdk.internal.logging.ErrorHandler
import io.embrace.android.embracesdk.internal.logs.LogOrchestrator
import io.embrace.android.embracesdk.internal.logs.LogOrchestratorImpl
import io.embrace.android.embracesdk.internal.otel.logs.LogSink
import io.embrace.android.embracesdk.internal.session.orchestrator.PayloadStore
import io.embrace.android.embracesdk.internal.worker.BackgroundWorker

fun createLogOrchestrator(
    worker: BackgroundWorker,
    clock: Clock,
    sink: LogSink,
    payloadStore: PayloadStore?,
    logEnvelopeSource: LogEnvelopeSource,
    errorHandler: ErrorHandler,
): LogOrchestrator = LogOrchestratorImpl(worker, clock, sink, payloadStore, logEnvelopeSource, errorHandler)

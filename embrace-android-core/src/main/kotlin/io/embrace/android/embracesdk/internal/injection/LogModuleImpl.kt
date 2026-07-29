package io.embrace.android.embracesdk.internal.injection

import io.embrace.android.embracesdk.internal.config.ConfigService
import io.embrace.android.embracesdk.internal.logs.LogLimitingService
import io.embrace.android.embracesdk.internal.logs.LogLimitingServiceImpl
import io.embrace.android.embracesdk.internal.logs.LogOrchestrator
import io.embrace.android.embracesdk.internal.logs.LogOrchestratorImpl
import io.embrace.android.embracesdk.internal.logs.LogService
import io.embrace.android.embracesdk.internal.logs.LogServiceImpl
import io.embrace.android.embracesdk.internal.logs.attachments.AttachmentService
import io.embrace.android.embracesdk.internal.worker.Worker

class LogModuleImpl(
    initModule: InitModule,
    openTelemetryModule: OpenTelemetryModule,
    essentialServiceModule: EssentialServiceModule,
    configService: ConfigService,
    deliveryModule: DeliveryModule?,
    workerThreadModule: WorkerThreadModule,
    payloadSourceModule: PayloadSourceModule,
) : LogModule {

    override val logLimitingService: LogLimitingService = LogLimitingServiceImpl(
        configService,
    )

    override val logOrchestrator: LogOrchestrator = LogOrchestratorImpl(
        workerThreadModule.backgroundWorker(Worker.Background.LogMessageWorker),
        initModule.clock,
        openTelemetryModule.logSink,
        deliveryModule?.payloadStore,
        payloadSourceModule.logEnvelopeSource,
    )

    override val attachmentService: AttachmentService = AttachmentService()

    /**
     * Deferred: only reached via the public logging API, never during SDK init.
     */
    override val logService: LogService by lazy(LazyThreadSafetyMode.PUBLICATION) {
        LogServiceImpl(
            essentialServiceModule.telemetryDestination,
            configService,
            logLimitingService,
            initModule.telemetryService,
        )
    }
}

package io.embrace.android.embracesdk.internal.injection

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
    configModule: ConfigModule,
    deliveryModule: DeliveryModule,
    workerThreadModule: WorkerThreadModule,
    payloadSourceModule: PayloadSourceModule,
) : LogModule {

    override val logService: LogService by singleton {
        LogServiceImpl(
            essentialServiceModule.telemetryDestination,
            configModule.configService,
            essentialServiceModule.sessionPropertiesService,
        )
    }

    override val logOrchestrator: LogOrchestrator by singleton {
        LogOrchestratorImpl(
            workerThreadModule.backgroundWorker(Worker.Background.LogMessageWorker),
            initModule.clock,
            openTelemetryModule.logSink,
            deliveryModule.payloadStore,
            payloadSourceModule.logEnvelopeSource,
        )
    }

    override val attachmentService: AttachmentService by singleton {
        AttachmentService()
    }
}

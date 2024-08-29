package io.embrace.android.embracesdk.internal.injection

import io.embrace.android.embracesdk.internal.logs.EmbraceLogService
import io.embrace.android.embracesdk.internal.logs.LogOrchestrator
import io.embrace.android.embracesdk.internal.logs.LogOrchestratorImpl
import io.embrace.android.embracesdk.internal.logs.LogService
import io.embrace.android.embracesdk.internal.network.logging.EmbraceDomainCountLimiter
import io.embrace.android.embracesdk.internal.network.logging.EmbraceNetworkCaptureService
import io.embrace.android.embracesdk.internal.network.logging.EmbraceNetworkLoggingService
import io.embrace.android.embracesdk.internal.network.logging.NetworkCaptureDataSource
import io.embrace.android.embracesdk.internal.network.logging.NetworkCaptureDataSourceImpl
import io.embrace.android.embracesdk.internal.network.logging.NetworkCaptureService
import io.embrace.android.embracesdk.internal.network.logging.NetworkLoggingService
import io.embrace.android.embracesdk.internal.worker.WorkerName

internal class LogModuleImpl(
    initModule: InitModule,
    openTelemetryModule: OpenTelemetryModule,
    androidServicesModule: AndroidServicesModule,
    essentialServiceModule: EssentialServiceModule,
    configModule: ConfigModule,
    deliveryModule: DeliveryModule,
    workerThreadModule: WorkerThreadModule,
    payloadSourceModule: PayloadSourceModule,
) : LogModule {

    override val networkCaptureService: NetworkCaptureService by singleton {
        EmbraceNetworkCaptureService(
            essentialServiceModule.sessionIdTracker,
            androidServicesModule.preferencesService,
            { networkCaptureDataSource },
            configModule.configService,
            initModule.jsonSerializer,
            initModule.logger
        )
    }

    override val networkCaptureDataSource: NetworkCaptureDataSource by singleton {
        NetworkCaptureDataSourceImpl(
            essentialServiceModule.logWriter,
            initModule.logger
        )
    }

    private val embraceDomainCountLimiter: EmbraceDomainCountLimiter by singleton {
        EmbraceDomainCountLimiter(
            configModule.configService,
            initModule.logger
        )
    }

    override val networkLoggingService: NetworkLoggingService by singleton {
        EmbraceNetworkLoggingService(
            embraceDomainCountLimiter,
            networkCaptureService,
            openTelemetryModule.spanService
        )
    }

    override val logService: LogService by singleton {
        EmbraceLogService(
            essentialServiceModule.logWriter,
            configModule.configService,
            essentialServiceModule.sessionPropertiesService,
            workerThreadModule.backgroundWorker(WorkerName.REMOTE_LOGGING),
            initModule.logger,
            initModule.jsonSerializer
        )
    }

    override val logOrchestrator: LogOrchestrator by singleton {
        LogOrchestratorImpl(
            workerThreadModule.scheduledWorker(WorkerName.REMOTE_LOGGING),
            initModule.clock,
            openTelemetryModule.logSink,
            deliveryModule.deliveryService,
            payloadSourceModule.logEnvelopeSource,
        )
    }
}

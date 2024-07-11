package io.embrace.android.embracesdk.injection

import io.embrace.android.embracesdk.internal.logs.EmbraceLogService
import io.embrace.android.embracesdk.internal.logs.LogOrchestrator
import io.embrace.android.embracesdk.internal.logs.LogOrchestratorImpl
import io.embrace.android.embracesdk.internal.logs.LogService
import io.embrace.android.embracesdk.network.logging.EmbraceDomainCountLimiter
import io.embrace.android.embracesdk.network.logging.EmbraceNetworkCaptureService
import io.embrace.android.embracesdk.network.logging.EmbraceNetworkLoggingService
import io.embrace.android.embracesdk.network.logging.NetworkCaptureDataSource
import io.embrace.android.embracesdk.network.logging.NetworkCaptureDataSourceImpl
import io.embrace.android.embracesdk.network.logging.NetworkCaptureService
import io.embrace.android.embracesdk.network.logging.NetworkLoggingService
import io.embrace.android.embracesdk.worker.WorkerName
import io.embrace.android.embracesdk.worker.WorkerThreadModule

/**
 * Holds dependencies that are required for a customer to send log messages to the backend.
 */
internal interface CustomerLogModule {
    val networkCaptureService: NetworkCaptureService
    val networkCaptureDataSource: NetworkCaptureDataSource
    val networkLoggingService: NetworkLoggingService
    val logService: LogService
    val logOrchestrator: LogOrchestrator
}

internal class CustomerLogModuleImpl(
    initModule: InitModule,
    openTelemetryModule: OpenTelemetryModule,
    androidServicesModule: AndroidServicesModule,
    essentialServiceModule: EssentialServiceModule,
    deliveryModule: DeliveryModule,
    workerThreadModule: WorkerThreadModule,
    payloadModule: PayloadModule,
) : CustomerLogModule {

    override val networkCaptureService: NetworkCaptureService by singleton {
        EmbraceNetworkCaptureService(
            essentialServiceModule.metadataService,
            essentialServiceModule.sessionIdTracker,
            androidServicesModule.preferencesService,
            { networkCaptureDataSource },
            essentialServiceModule.configService,
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
            essentialServiceModule.configService,
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
            essentialServiceModule.configService,
            essentialServiceModule.sessionProperties,
            workerThreadModule.backgroundWorker(WorkerName.REMOTE_LOGGING),
            initModule.logger,
            initModule.clock,
            initModule.jsonSerializer
        )
    }

    override val logOrchestrator: LogOrchestrator by singleton {
        LogOrchestratorImpl(
            workerThreadModule.scheduledWorker(WorkerName.REMOTE_LOGGING),
            initModule.clock,
            openTelemetryModule.logSink,
            deliveryModule.deliveryService,
            payloadModule.logEnvelopeSource,
        )
    }
}

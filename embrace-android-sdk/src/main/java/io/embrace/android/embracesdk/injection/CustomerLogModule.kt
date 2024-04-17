package io.embrace.android.embracesdk.injection

import io.embrace.android.embracesdk.event.EmbraceLogMessageService
import io.embrace.android.embracesdk.event.LogMessageService
import io.embrace.android.embracesdk.internal.logs.CompositeLogService
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
    val logMessageService: LogMessageService
    val logOrchestrator: LogOrchestrator
}

internal class CustomerLogModuleImpl(
    initModule: InitModule,
    coreModule: CoreModule,
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
            logMessageService,
            essentialServiceModule.configService,
            coreModule.jsonSerializer,
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

    private val v1LogService: LogMessageService by singleton {
        EmbraceLogMessageService(
            essentialServiceModule.metadataService,
            essentialServiceModule.sessionIdTracker,
            deliveryModule.deliveryService,
            essentialServiceModule.userService,
            essentialServiceModule.configService,
            essentialServiceModule.sessionProperties,
            initModule.logger,
            initModule.clock,
            essentialServiceModule.gatingService,
            essentialServiceModule.networkConnectivityService,
            workerThreadModule.backgroundWorker(WorkerName.REMOTE_LOGGING)
        )
    }

    private val v2LogService: LogService by singleton {
        EmbraceLogService(
            essentialServiceModule.logWriter,
            essentialServiceModule.configService,
            coreModule.appFramework,
            essentialServiceModule.sessionProperties,
            workerThreadModule.backgroundWorker(WorkerName.REMOTE_LOGGING),
            initModule.logger,
            initModule.clock,
        )
    }

    override val logMessageService: LogMessageService by singleton {
        CompositeLogService(
            { v1LogService },
            { v2LogService },
            { networkCaptureDataSource },
            essentialServiceModule.configService,
            initModule.logger,
            coreModule.jsonSerializer
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

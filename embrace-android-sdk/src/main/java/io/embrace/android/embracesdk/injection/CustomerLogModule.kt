package io.embrace.android.embracesdk.injection

import io.embrace.android.embracesdk.event.EmbraceLogMessageService
import io.embrace.android.embracesdk.event.LogMessageService
import io.embrace.android.embracesdk.internal.logs.EmbraceLogService
import io.embrace.android.embracesdk.internal.logs.LogOrchestrator
import io.embrace.android.embracesdk.internal.logs.LogService
import io.embrace.android.embracesdk.network.logging.EmbraceNetworkCaptureService
import io.embrace.android.embracesdk.network.logging.EmbraceNetworkLoggingService
import io.embrace.android.embracesdk.network.logging.NetworkCaptureService
import io.embrace.android.embracesdk.network.logging.NetworkLoggingService
import io.embrace.android.embracesdk.session.properties.EmbraceSessionProperties
import io.embrace.android.embracesdk.worker.WorkerName
import io.embrace.android.embracesdk.worker.WorkerThreadModule

/**
 * Holds dependencies that are required for a customer to send log messages to the backend.
 */
internal interface CustomerLogModule {
    val networkCaptureService: NetworkCaptureService
    val networkLoggingService: NetworkLoggingService
    val logMessageService: LogMessageService
    val logService: LogService
    val logOrchestrator: LogOrchestrator
}

internal class CustomerLogModuleImpl(
    initModule: InitModule,
    coreModule: CoreModule,
    openTelemetryModule: OpenTelemetryModule,
    androidServicesModule: AndroidServicesModule,
    essentialServiceModule: EssentialServiceModule,
    deliveryModule: DeliveryModule,
    sessionProperties: EmbraceSessionProperties,
    workerThreadModule: WorkerThreadModule
) : CustomerLogModule {

    override val networkCaptureService: NetworkCaptureService by singleton {
        EmbraceNetworkCaptureService(
            essentialServiceModule.metadataService,
            essentialServiceModule.sessionIdTracker,
            androidServicesModule.preferencesService,
            logMessageService,
            essentialServiceModule.configService,
            coreModule.jsonSerializer
        )
    }

    override val networkLoggingService: NetworkLoggingService by singleton {
        EmbraceNetworkLoggingService(
            essentialServiceModule.configService,
            coreModule.logger,
            networkCaptureService
        )
    }

    override val logMessageService: LogMessageService by singleton {
        EmbraceLogMessageService(
            essentialServiceModule.metadataService,
            essentialServiceModule.sessionIdTracker,
            deliveryModule.deliveryService,
            essentialServiceModule.userService,
            essentialServiceModule.configService,
            sessionProperties,
            coreModule.logger,
            initModule.clock,
            essentialServiceModule.gatingService,
            essentialServiceModule.networkConnectivityService,
            workerThreadModule.backgroundWorker(WorkerName.REMOTE_LOGGING)
        )
    }

    override val logService: LogService by singleton {
        EmbraceLogService(
            openTelemetryModule.logWriter,
            initModule.clock,
            essentialServiceModule.metadataService,
            essentialServiceModule.sessionIdTracker,
            workerThreadModule.backgroundWorker(WorkerName.REMOTE_LOGGING)
        )
    }

    override val logOrchestrator: LogOrchestrator by singleton {
        LogOrchestrator(
            workerThreadModule.scheduledWorker(WorkerName.REMOTE_LOGGING),
            initModule.clock,
            openTelemetryModule.logSink,
            deliveryModule.deliveryService
        )
    }
}

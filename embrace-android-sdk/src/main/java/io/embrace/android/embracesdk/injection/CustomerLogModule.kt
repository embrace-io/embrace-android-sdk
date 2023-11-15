package io.embrace.android.embracesdk.injection

import io.embrace.android.embracesdk.event.EmbraceRemoteLogger
import io.embrace.android.embracesdk.network.logging.EmbraceNetworkCaptureService
import io.embrace.android.embracesdk.network.logging.EmbraceNetworkLoggingService
import io.embrace.android.embracesdk.network.logging.NetworkCaptureService
import io.embrace.android.embracesdk.network.logging.NetworkLoggingService
import io.embrace.android.embracesdk.session.properties.EmbraceSessionProperties
import io.embrace.android.embracesdk.worker.ExecutorName
import io.embrace.android.embracesdk.worker.WorkerThreadModule

/**
 * Holds dependencies that are required for a customer to send log messages to the backend.
 */
internal interface CustomerLogModule {
    val networkCaptureService: NetworkCaptureService
    val networkLoggingService: NetworkLoggingService
    val remoteLogger: EmbraceRemoteLogger
}

internal class CustomerLogModuleImpl(
    initModule: InitModule,
    coreModule: CoreModule,
    androidServicesModule: AndroidServicesModule,
    essentialServiceModule: EssentialServiceModule,
    deliveryModule: DeliveryModule,
    sessionProperties: EmbraceSessionProperties,
    workerThreadModule: WorkerThreadModule
) : CustomerLogModule {

    override val networkCaptureService: NetworkCaptureService by singleton {
        EmbraceNetworkCaptureService(
            essentialServiceModule.metadataService,
            androidServicesModule.preferencesService,
            remoteLogger,
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

    override val remoteLogger: EmbraceRemoteLogger by singleton {
        EmbraceRemoteLogger(
            essentialServiceModule.metadataService,
            deliveryModule.deliveryService,
            essentialServiceModule.userService,
            essentialServiceModule.configService,
            sessionProperties,
            coreModule.logger,
            initModule.clock,
            essentialServiceModule.gatingService,
            essentialServiceModule.networkConnectivityService,
            workerThreadModule.backgroundExecutor(ExecutorName.REMOTE_LOGGING)
        )
    }
}

package io.embrace.android.embracesdk.injection

import io.embrace.android.embracesdk.ndk.NativeModule
import io.embrace.android.embracesdk.session.BackgroundActivityService
import io.embrace.android.embracesdk.session.EmbraceBackgroundActivityService
import io.embrace.android.embracesdk.session.EmbraceSessionService
import io.embrace.android.embracesdk.session.PayloadMessageCollator
import io.embrace.android.embracesdk.session.SessionHandler
import io.embrace.android.embracesdk.session.SessionService
import io.embrace.android.embracesdk.session.properties.EmbraceSessionProperties
import io.embrace.android.embracesdk.session.properties.EmbraceSessionPropertiesService
import io.embrace.android.embracesdk.session.properties.SessionPropertiesService
import io.embrace.android.embracesdk.worker.WorkerName
import io.embrace.android.embracesdk.worker.WorkerThreadModule

internal interface SessionModule {
    val sessionHandler: SessionHandler
    val sessionService: SessionService
    val backgroundActivityService: BackgroundActivityService?
    val payloadMessageCollator: PayloadMessageCollator
    val sessionPropertiesService: SessionPropertiesService
}

internal class SessionModuleImpl(
    initModule: InitModule,
    coreModule: CoreModule,
    androidServicesModule: AndroidServicesModule,
    essentialServiceModule: EssentialServiceModule,
    nativeModule: NativeModule,
    dataContainerModule: DataContainerModule,
    deliveryModule: DeliveryModule,
    sessionProperties: EmbraceSessionProperties,
    dataCaptureServiceModule: DataCaptureServiceModule,
    customerLogModule: CustomerLogModule,
    sdkObservabilityModule: SdkObservabilityModule,
    workerThreadModule: WorkerThreadModule
) : SessionModule {

    override val payloadMessageCollator: PayloadMessageCollator by singleton {
        PayloadMessageCollator(
            essentialServiceModule.configService,
            essentialServiceModule.metadataService,
            dataContainerModule.eventService,
            customerLogModule.logMessageService,
            sdkObservabilityModule.internalErrorService,
            dataContainerModule.performanceInfoService,
            dataCaptureServiceModule.webviewService,
            dataCaptureServiceModule.thermalStatusService,
            nativeModule.nativeThreadSamplerService,
            dataCaptureServiceModule.breadcrumbService,
            essentialServiceModule.userService,
            androidServicesModule.preferencesService,
            initModule.spansService,
            initModule.clock
        )
    }

    override val sessionHandler: SessionHandler by singleton {
        SessionHandler(
            coreModule.logger,
            essentialServiceModule.configService,
            essentialServiceModule.userService,
            essentialServiceModule.networkConnectivityService,
            essentialServiceModule.metadataService,
            dataCaptureServiceModule.breadcrumbService,
            essentialServiceModule.activityLifecycleTracker,
            nativeModule.ndkService,
            sdkObservabilityModule.internalErrorService,
            essentialServiceModule.memoryCleanerService,
            deliveryModule.deliveryService,
            payloadMessageCollator,
            sessionProperties,
            initModule.clock,
            initModule.spansService,
            workerThreadModule.scheduledWorker(WorkerName.BACKGROUND_REGISTRATION),
            workerThreadModule.scheduledWorker(WorkerName.PERIODIC_CACHE)
        )
    }

    override val sessionPropertiesService: SessionPropertiesService by singleton {
        EmbraceSessionPropertiesService(
            nativeModule.ndkService,
            sessionProperties
        )
    }

    override val sessionService: SessionService by singleton {
        EmbraceSessionService(
            essentialServiceModule.processStateService,
            nativeModule.ndkService,
            sessionHandler,
            deliveryModule.deliveryService,
            essentialServiceModule.configService.autoDataCaptureBehavior.isNdkEnabled(),
            essentialServiceModule.configService,
            initModule.clock,
            coreModule.logger
        )
    }

    override val backgroundActivityService: BackgroundActivityService? by singleton {
        if (essentialServiceModule.configService.isBackgroundActivityCaptureEnabled()) {
            EmbraceBackgroundActivityService(
                essentialServiceModule.metadataService,
                essentialServiceModule.processStateService,
                deliveryModule.deliveryService,
                essentialServiceModule.configService,
                nativeModule.ndkService,
                initModule.clock,
                payloadMessageCollator,
                workerThreadModule.scheduledWorker(WorkerName.PERIODIC_CACHE)
            )
        } else {
            null
        }
    }
}

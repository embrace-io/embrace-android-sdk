package io.embrace.android.embracesdk.injection

import io.embrace.android.embracesdk.ndk.NativeModule
import io.embrace.android.embracesdk.session.BackgroundActivityService
import io.embrace.android.embracesdk.session.EmbraceBackgroundActivityService
import io.embrace.android.embracesdk.session.EmbraceSessionService
import io.embrace.android.embracesdk.session.PayloadMessageCollator
import io.embrace.android.embracesdk.session.SessionService
import io.embrace.android.embracesdk.session.orchestrator.SessionOrchestrator
import io.embrace.android.embracesdk.session.orchestrator.SessionOrchestratorImpl
import io.embrace.android.embracesdk.session.properties.EmbraceSessionProperties
import io.embrace.android.embracesdk.session.properties.EmbraceSessionPropertiesService
import io.embrace.android.embracesdk.session.properties.SessionPropertiesService
import io.embrace.android.embracesdk.worker.WorkerName
import io.embrace.android.embracesdk.worker.WorkerThreadModule

internal interface SessionModule {
    val sessionService: SessionService
    val backgroundActivityService: BackgroundActivityService?
    val payloadMessageCollator: PayloadMessageCollator
    val sessionPropertiesService: SessionPropertiesService
    val sessionOrchestrator: SessionOrchestrator
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

    override val sessionPropertiesService: SessionPropertiesService by singleton {
        EmbraceSessionPropertiesService(
            nativeModule.ndkService,
            sessionProperties
        )
    }

    override val sessionService: SessionService by singleton {
        val ndkService = when {
            essentialServiceModule.configService.autoDataCaptureBehavior.isNdkEnabled() -> nativeModule.ndkService
            else -> null
        }
        EmbraceSessionService(
            coreModule.logger,
            essentialServiceModule.configService,
            essentialServiceModule.userService,
            essentialServiceModule.networkConnectivityService,
            essentialServiceModule.metadataService,
            dataCaptureServiceModule.breadcrumbService,
            ndkService,
            sdkObservabilityModule.internalErrorService,
            essentialServiceModule.memoryCleanerService,
            deliveryModule.deliveryService,
            payloadMessageCollator,
            sessionProperties,
            initModule.clock,
            initModule.spansService,
            workerThreadModule.scheduledWorker(WorkerName.PERIODIC_CACHE)
        )
    }

    override val backgroundActivityService: BackgroundActivityService? by singleton {
        EmbraceBackgroundActivityService(
            essentialServiceModule.metadataService,
            deliveryModule.deliveryService,
            essentialServiceModule.configService,
            nativeModule.ndkService,
            initModule.clock,
            payloadMessageCollator,
            workerThreadModule.scheduledWorker(WorkerName.PERIODIC_CACHE)
        )
    }

    override val sessionOrchestrator: SessionOrchestrator by singleton {
        SessionOrchestratorImpl(
            essentialServiceModule.processStateService,
            sessionService,
            backgroundActivityService,
            initModule.clock,
            essentialServiceModule.configService
        )
    }
}

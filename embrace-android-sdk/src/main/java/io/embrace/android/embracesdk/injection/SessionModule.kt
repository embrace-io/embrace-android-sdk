package io.embrace.android.embracesdk.injection

import io.embrace.android.embracesdk.ndk.NativeModule
import io.embrace.android.embracesdk.session.caching.PeriodicBackgroundActivityCacher
import io.embrace.android.embracesdk.session.caching.PeriodicSessionCacher
import io.embrace.android.embracesdk.session.message.BackgroundActivityService
import io.embrace.android.embracesdk.session.message.EmbraceBackgroundActivityService
import io.embrace.android.embracesdk.session.message.EmbraceSessionService
import io.embrace.android.embracesdk.session.message.PayloadMessageCollator
import io.embrace.android.embracesdk.session.message.SessionService
import io.embrace.android.embracesdk.session.orchestrator.OrchestratorBoundaryDelegate
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
    val periodicSessionCacher: PeriodicSessionCacher
    val periodicBackgroundActivityCacher: PeriodicBackgroundActivityCacher
}

internal class SessionModuleImpl(
    initModule: InitModule,
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
            initModule.clock,
            sessionPropertiesService,
            dataCaptureServiceModule.startupService
        )
    }

    override val sessionPropertiesService: SessionPropertiesService by singleton {
        EmbraceSessionPropertiesService(
            nativeModule.ndkService,
            sessionProperties
        )
    }

    private val ndkService by singleton {
        when {
            essentialServiceModule.configService.autoDataCaptureBehavior.isNdkEnabled() -> nativeModule.ndkService
            else -> null
        }
    }

    override val periodicSessionCacher: PeriodicSessionCacher by singleton {
        PeriodicSessionCacher(
            initModule.clock,
            workerThreadModule.scheduledWorker(WorkerName.PERIODIC_CACHE)
        )
    }

    override val periodicBackgroundActivityCacher: PeriodicBackgroundActivityCacher by singleton {
        PeriodicBackgroundActivityCacher(
            initModule.clock,
            workerThreadModule.scheduledWorker(WorkerName.PERIODIC_CACHE)
        )
    }

    override val sessionService: SessionService by singleton {
        EmbraceSessionService(
            deliveryModule.deliveryService,
            payloadMessageCollator
        )
    }

    override val backgroundActivityService: BackgroundActivityService? by singleton {
        EmbraceBackgroundActivityService(
            deliveryModule.deliveryService,
            payloadMessageCollator
        )
    }

    private val boundaryDelegate by singleton {
        OrchestratorBoundaryDelegate(
            essentialServiceModule.memoryCleanerService,
            essentialServiceModule.userService,
            ndkService,
            sessionProperties,
            sdkObservabilityModule.internalErrorService,
            essentialServiceModule.networkConnectivityService,
            dataCaptureServiceModule.breadcrumbService,
        )
    }

    override val sessionOrchestrator: SessionOrchestrator by singleton(LoadType.EAGER) {
        SessionOrchestratorImpl(
            essentialServiceModule.processStateService,
            sessionService,
            backgroundActivityService,
            initModule.clock,
            essentialServiceModule.configService,
            essentialServiceModule.sessionIdTracker,
            boundaryDelegate,
            periodicSessionCacher,
            periodicBackgroundActivityCacher
        )
    }
}

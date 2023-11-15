package io.embrace.android.embracesdk.injection

import io.embrace.android.embracesdk.ndk.NativeModule
import io.embrace.android.embracesdk.session.BackgroundActivityService
import io.embrace.android.embracesdk.session.EmbraceBackgroundActivityService
import io.embrace.android.embracesdk.session.EmbraceSessionProperties
import io.embrace.android.embracesdk.session.EmbraceSessionService
import io.embrace.android.embracesdk.session.SessionHandler
import io.embrace.android.embracesdk.session.SessionService
import io.embrace.android.embracesdk.worker.ExecutorName
import io.embrace.android.embracesdk.worker.WorkerThreadModule

internal interface SessionModule {
    val sessionHandler: SessionHandler
    val sessionService: SessionService
    val backgroundActivityService: BackgroundActivityService?
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

    override val sessionHandler: SessionHandler by singleton {
        SessionHandler(
            coreModule.logger,
            essentialServiceModule.configService,
            androidServicesModule.preferencesService,
            essentialServiceModule.userService,
            essentialServiceModule.networkConnectivityService,
            essentialServiceModule.metadataService,
            dataCaptureServiceModule.breadcrumbService,
            essentialServiceModule.activityLifecycleTracker,
            nativeModule.ndkService,
            dataContainerModule.eventService,
            customerLogModule.remoteLogger,
            sdkObservabilityModule.exceptionService,
            dataContainerModule.performanceInfoService,
            essentialServiceModule.memoryCleanerService,
            deliveryModule.deliveryService,
            dataCaptureServiceModule.webviewService,
            dataCaptureServiceModule.activityLifecycleBreadcrumbService,
            dataCaptureServiceModule.thermalStatusService,
            nativeModule.nativeThreadSamplerService,
            initModule.clock,
            workerThreadModule.scheduledExecutor(ExecutorName.SESSION_CLOSER),
            workerThreadModule.scheduledExecutor(ExecutorName.SESSION_CACHING)
        )
    }

    override val sessionService: SessionService by singleton {
        EmbraceSessionService(
            essentialServiceModule.processStateService,
            nativeModule.ndkService,
            sessionProperties,
            coreModule.logger,
            sessionHandler,
            deliveryModule.deliveryService,
            essentialServiceModule.configService.autoDataCaptureBehavior.isNdkEnabled(),
            initModule.clock,
            initModule.spansService
        )
    }

    override val backgroundActivityService: BackgroundActivityService? by singleton {
        if (essentialServiceModule.configService.isBackgroundActivityCaptureEnabled()) {
            EmbraceBackgroundActivityService(
                dataContainerModule.performanceInfoService,
                essentialServiceModule.metadataService,
                dataCaptureServiceModule.breadcrumbService,
                essentialServiceModule.processStateService,
                dataContainerModule.eventService,
                customerLogModule.remoteLogger,
                essentialServiceModule.userService,
                sdkObservabilityModule.exceptionService,
                deliveryModule.deliveryService,
                essentialServiceModule.configService,
                nativeModule.ndkService,
                initModule.clock,
                initModule.spansService,
                lazy { workerThreadModule.backgroundExecutor(ExecutorName.SESSION_CACHE_EXECUTOR) }
            )
        } else {
            null
        }
    }
}

package io.embrace.android.embracesdk.injection

import io.embrace.android.embracesdk.arch.DataCaptureOrchestrator
import io.embrace.android.embracesdk.ndk.NativeModule
import io.embrace.android.embracesdk.session.caching.PeriodicBackgroundActivityCacher
import io.embrace.android.embracesdk.session.caching.PeriodicSessionCacher
import io.embrace.android.embracesdk.session.message.PayloadFactory
import io.embrace.android.embracesdk.session.message.PayloadFactoryImpl
import io.embrace.android.embracesdk.session.message.V1PayloadMessageCollator
import io.embrace.android.embracesdk.session.message.V2PayloadMessageCollator
import io.embrace.android.embracesdk.session.orchestrator.OrchestratorBoundaryDelegate
import io.embrace.android.embracesdk.session.orchestrator.SessionOrchestrator
import io.embrace.android.embracesdk.session.orchestrator.SessionOrchestratorImpl
import io.embrace.android.embracesdk.session.properties.EmbraceSessionPropertiesService
import io.embrace.android.embracesdk.session.properties.SessionPropertiesService
import io.embrace.android.embracesdk.worker.WorkerName
import io.embrace.android.embracesdk.worker.WorkerThreadModule

internal interface SessionModule {
    val payloadFactory: PayloadFactory
    val v1PayloadMessageCollator: V1PayloadMessageCollator
    val v2PayloadMessageCollator: V2PayloadMessageCollator
    val sessionPropertiesService: SessionPropertiesService
    val sessionOrchestrator: SessionOrchestrator
    val periodicSessionCacher: PeriodicSessionCacher
    val periodicBackgroundActivityCacher: PeriodicBackgroundActivityCacher
    val dataCaptureOrchestrator: DataCaptureOrchestrator
}

internal class SessionModuleImpl(
    initModule: InitModule,
    openTelemetryModule: OpenTelemetryModule,
    androidServicesModule: AndroidServicesModule,
    essentialServiceModule: EssentialServiceModule,
    nativeModule: NativeModule,
    dataContainerModule: DataContainerModule,
    deliveryModule: DeliveryModule,
    dataCaptureServiceModule: DataCaptureServiceModule,
    customerLogModule: CustomerLogModule,
    sdkObservabilityModule: SdkObservabilityModule,
    workerThreadModule: WorkerThreadModule,
    dataSourceModule: DataSourceModule,
    payloadModule: PayloadModule
) : SessionModule {

    override val v1PayloadMessageCollator: V1PayloadMessageCollator by singleton {
        V1PayloadMessageCollator(
            essentialServiceModule.gatingService,
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
            openTelemetryModule.spanRepository,
            openTelemetryModule.spanSink,
            openTelemetryModule.currentSessionSpan,
            sessionPropertiesService,
            dataCaptureServiceModule.startupService,
            initModule.logger
        )
    }

    override val v2PayloadMessageCollator: V2PayloadMessageCollator by singleton {
        V2PayloadMessageCollator(
            essentialServiceModule.gatingService,
            v1PayloadMessageCollator,
            payloadModule.sessionEnvelopeSource,
            initModule.logger
        )
    }

    override val sessionPropertiesService: SessionPropertiesService by singleton {
        EmbraceSessionPropertiesService(
            nativeModule.ndkService,
            essentialServiceModule.sessionProperties
        )
    }

    private val ndkService by singleton {
        when {
            essentialServiceModule.configService.autoDataCaptureBehavior.isNdkEnabled() -> nativeModule.ndkService
            else -> null
        }
    }

    override val periodicSessionCacher: PeriodicSessionCacher by singleton {
        PeriodicSessionCacher(workerThreadModule.scheduledWorker(WorkerName.PERIODIC_CACHE), initModule.logger)
    }

    override val periodicBackgroundActivityCacher: PeriodicBackgroundActivityCacher by singleton {
        PeriodicBackgroundActivityCacher(
            initModule.clock,
            workerThreadModule.scheduledWorker(WorkerName.PERIODIC_CACHE),
            initModule.logger
        )
    }

    override val payloadFactory: PayloadFactory by singleton {
        PayloadFactoryImpl(
            v1PayloadMessageCollator,
            v2PayloadMessageCollator,
            essentialServiceModule.configService,
            initModule.logger
        )
    }

    private val boundaryDelegate by singleton {
        OrchestratorBoundaryDelegate(
            essentialServiceModule.memoryCleanerService,
            essentialServiceModule.userService,
            ndkService,
            essentialServiceModule.sessionProperties,
            sdkObservabilityModule.internalErrorService,
            essentialServiceModule.networkConnectivityService,
            dataCaptureServiceModule.breadcrumbService,
        )
    }

    override val dataCaptureOrchestrator: DataCaptureOrchestrator by singleton {
        val dataSources = dataSourceModule.getDataSources()
        DataCaptureOrchestrator(dataSources, initModule.logger).apply {
            essentialServiceModule.configService.addListener(this)
        }
    }

    override val sessionOrchestrator: SessionOrchestrator by singleton(LoadType.EAGER) {
        SessionOrchestratorImpl(
            essentialServiceModule.processStateService,
            payloadFactory,
            initModule.clock,
            essentialServiceModule.configService,
            essentialServiceModule.sessionIdTracker,
            boundaryDelegate,
            deliveryModule.deliveryService,
            periodicSessionCacher,
            periodicBackgroundActivityCacher,
            dataCaptureOrchestrator,
            initModule.logger
        )
    }
}

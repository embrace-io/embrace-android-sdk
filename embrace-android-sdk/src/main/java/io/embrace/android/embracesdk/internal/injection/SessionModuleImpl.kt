package io.embrace.android.embracesdk.internal.injection

import io.embrace.android.embracesdk.internal.capture.session.EmbraceSessionPropertiesService
import io.embrace.android.embracesdk.internal.capture.session.SessionPropertiesService
import io.embrace.android.embracesdk.internal.session.caching.PeriodicBackgroundActivityCacher
import io.embrace.android.embracesdk.internal.session.caching.PeriodicSessionCacher
import io.embrace.android.embracesdk.internal.session.message.PayloadFactory
import io.embrace.android.embracesdk.internal.session.message.PayloadFactoryImpl
import io.embrace.android.embracesdk.internal.session.message.PayloadMessageCollatorImpl
import io.embrace.android.embracesdk.internal.session.orchestrator.OrchestratorBoundaryDelegate
import io.embrace.android.embracesdk.internal.session.orchestrator.SessionOrchestrator
import io.embrace.android.embracesdk.internal.session.orchestrator.SessionOrchestratorImpl
import io.embrace.android.embracesdk.internal.session.orchestrator.SessionSpanAttrPopulator
import io.embrace.android.embracesdk.internal.worker.WorkerName

internal class SessionModuleImpl(
    initModule: InitModule,
    openTelemetryModule: OpenTelemetryModule,
    androidServicesModule: AndroidServicesModule,
    essentialServiceModule: EssentialServiceModule,
    nativeModule: NativeModule,
    deliveryModule: DeliveryModule,
    workerThreadModule: WorkerThreadModule,
    dataSourceModule: DataSourceModule,
    payloadModule: PayloadModule,
    dataCaptureServiceModule: DataCaptureServiceModule,
    dataContainerModule: DataContainerModule,
    customerLogModule: CustomerLogModule
) : SessionModule {

    override val payloadMessageCollatorImpl: PayloadMessageCollatorImpl by singleton {
        PayloadMessageCollatorImpl(
            essentialServiceModule.gatingService,
            payloadModule.sessionEnvelopeSource,
            androidServicesModule.preferencesService,
            openTelemetryModule.currentSessionSpan
        )
    }

    override val sessionPropertiesService: SessionPropertiesService by singleton {
        EmbraceSessionPropertiesService(
            nativeModule.ndkService::onSessionPropertiesUpdate,
            essentialServiceModule.sessionProperties
        ) { dataSourceModule.sessionPropertiesDataSource.dataSource }
    }

    private val ndkService by singleton {
        when {
            essentialServiceModule.configService.autoDataCaptureBehavior.isNdkEnabled() -> nativeModule.ndkService
            else -> null
        }
    }

    override val periodicSessionCacher: PeriodicSessionCacher by singleton {
        PeriodicSessionCacher(
            workerThreadModule.scheduledWorker(WorkerName.PERIODIC_CACHE),
            initModule.logger
        )
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
            payloadMessageCollatorImpl,
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
            essentialServiceModule.networkConnectivityService
        )
    }

    private val sessionSpanAttrPopulator by singleton {
        SessionSpanAttrPopulator(
            openTelemetryModule.currentSessionSpan,
            dataContainerModule.eventService,
            dataCaptureServiceModule.startupService,
            customerLogModule.logService,
            essentialServiceModule.metadataService
        )
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
            dataSourceModule.dataCaptureOrchestrator,
            openTelemetryModule.currentSessionSpan,
            sessionSpanAttrPopulator,
            initModule.logger
        )
    }
}

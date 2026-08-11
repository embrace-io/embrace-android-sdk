package io.embrace.android.embracesdk.internal.injection

import io.embrace.android.embracesdk.internal.config.ConfigService
import io.embrace.android.embracesdk.internal.session.UserSessionMetadataStore
import io.embrace.android.embracesdk.internal.session.id.SessionIdsProvider
import io.embrace.android.embracesdk.internal.session.message.PayloadFactoryImpl
import io.embrace.android.embracesdk.internal.session.message.PayloadMessageCollatorImpl
import io.embrace.android.embracesdk.internal.session.orchestrator.OrchestratorBoundaryDelegate
import io.embrace.android.embracesdk.internal.session.orchestrator.SessionOrchestrator
import io.embrace.android.embracesdk.internal.session.orchestrator.SessionOrchestratorImpl
import io.embrace.android.embracesdk.internal.session.orchestrator.SessionPartSpanAttrPopulatorImpl
import io.embrace.android.embracesdk.internal.worker.Worker

class UserSessionOrchestrationModuleImpl(
    initModule: InitModule,
    openTelemetryModule: OpenTelemetryModule,
    coreModule: CoreModule,
    private val essentialServiceModule: EssentialServiceModule,
    configService: ConfigService,
    deliveryModule: DeliveryModule?,
    instrumentationModule: InstrumentationModule,
    payloadSourceModule: PayloadSourceModule,
    startupDurationProvider: () -> Long?,
    appVersionStartupCounterProvider: () -> Int?,
    logModule: LogModule,
    workerThreadModule: WorkerThreadModule,
) : UserSessionOrchestrationModule {

    override val sessionIdsProvider: SessionIdsProvider get() = essentialServiceModule.sessionIdsProvider

    override val sessionOrchestrator: SessionOrchestrator = run {
        val payloadMessageCollator = PayloadMessageCollatorImpl(
            payloadSourceModule.sessionPartEnvelopeSource,
            openTelemetryModule.currentSessionPartSpan,
            essentialServiceModule.sessionIdsProvider,
        )

        val payloadFactory = PayloadFactoryImpl(
            payloadMessageCollator,
            payloadSourceModule.logEnvelopeSource,
            configService,
            initModule.logger,
        )

        val boundaryDelegate = OrchestratorBoundaryDelegate(
            essentialServiceModule.userSessionPropertiesService,
        )

        val sessionPartSpanAttrPopulator = SessionPartSpanAttrPopulatorImpl(
            essentialServiceModule.telemetryDestination,
            startupDurationProvider,
            appVersionStartupCounterProvider,
            logModule.logLimitingService,
            payloadSourceModule.metadataService,
        )

        SessionOrchestratorImpl(
            essentialServiceModule.processStateTracker,
            payloadFactory,
            initModule.clock,
            configService,
            essentialServiceModule.sessionPartTracker,
            boundaryDelegate,
            deliveryModule?.payloadStore,
            deliveryModule?.payloadCachingService,
            instrumentationModule.instrumentationRegistry,
            essentialServiceModule.telemetryDestination,
            sessionPartSpanAttrPopulator,
            coreModule.ordinalStore,
            coreModule.store,
            UserSessionMetadataStore(coreModule.store),
            initModule.logger,
            workerThreadModule.backgroundWorker(Worker.Background.NonIoRegWorker),
            initModule.uuidSource,
            initModule.startupClassifier,
        )
    }
}

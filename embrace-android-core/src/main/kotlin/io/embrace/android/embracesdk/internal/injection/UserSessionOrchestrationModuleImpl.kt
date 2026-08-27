package io.embrace.android.embracesdk.internal.injection

import io.embrace.android.embracesdk.internal.arch.schema.EmbType
import io.embrace.android.embracesdk.internal.config.ConfigService
import io.embrace.android.embracesdk.internal.delivery.storage.StorageLocation
import io.embrace.android.embracesdk.internal.delivery.storage.asFile
import io.embrace.android.embracesdk.internal.otel.spans.EmbraceSdkSpan
import io.embrace.android.embracesdk.internal.otel.spans.hasEmbraceAttribute
import io.embrace.android.embracesdk.internal.resurrection.SessionPartReader
import io.embrace.android.embracesdk.internal.session.UserSessionMetadataStore
import io.embrace.android.embracesdk.internal.session.id.SessionIdsProvider
import io.embrace.android.embracesdk.internal.session.message.PayloadFactoryImpl
import io.embrace.android.embracesdk.internal.session.message.PayloadMessageCollatorImpl
import io.embrace.android.embracesdk.internal.session.orchestrator.OrchestratorBoundaryDelegate
import io.embrace.android.embracesdk.internal.session.orchestrator.SessionOrchestrator
import io.embrace.android.embracesdk.internal.session.orchestrator.SessionOrchestratorImpl
import io.embrace.android.embracesdk.internal.session.orchestrator.SessionPartSpanAttrPopulatorImpl
import io.embrace.android.embracesdk.internal.session.orchestrator.SessionPartWriterImpl
import io.embrace.android.embracesdk.internal.session.persistence.SessionPartDirectoryStore
import io.embrace.android.embracesdk.internal.session.persistence.SessionReconstructionService
import io.embrace.android.embracesdk.internal.worker.Worker
import java.io.File

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

    private val sessionsDir: Lazy<File> = StorageLocation.SESSION_SPLIT.asFile(
        logger = initModule.logger,
        rootDirSupplier = { coreModule.context.filesDir },
        fallbackDirSupplier = { coreModule.context.cacheDir },
    )

    private val sessionPersistenceWorker = workerThreadModule.backgroundWorker(Worker.Background.SessionPersistenceWorker)

    private val sessionPartDirectoryStore = SessionPartDirectoryStore(
        sessionsDir,
        sessionPersistenceWorker,
        initModule.clock,
        initModule.logger,
    )

    override val sessionPartReader: SessionPartReader? = deliveryModule?.let { delivery ->
        SessionPartReader(
            directoryStore = sessionPartDirectoryStore,
            reconstructionService = SessionReconstructionService(sessionsDir, initModule.logger),
            intakeService = delivery.intakeService,
            processIdProvider = { openTelemetryModule.otelSdkConfig.processIdentifier },
            configService = configService,
            logger = initModule.logger,
        )
    }

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

        val sessionPartWriter = SessionPartWriterImpl(
            sessionsDir,
            sessionPersistenceWorker,
            configService,
            initModule.uuidSource,
            initModule.clock,
            initModule.logger,
            payloadSourceModule.resourceSource,
            payloadSourceModule.envelopeMetadataSource,
            openTelemetryModule.currentSessionPartSpan,
            {
                // don't include the session part span
                openTelemetryModule.spanRepository.getActiveEmbraceSpans()
                    .filterNot { it.hasEmbraceAttribute(EmbType.Ux.Session) }
                    .mapNotNull(EmbraceSdkSpan::snapshot)
            },
            sessionPartDirectoryStore,
        )
        essentialServiceModule.userService.addUserInfoListener(sessionPartWriter::onMetadataChanged)
        openTelemetryModule.spanRepository.addCompletedOtelSpansListener { spans ->
            // don't include the session part span
            sessionPartWriter.onSpanCompleted(spans.filterNot { it.hasEmbraceAttribute(EmbType.Ux.Session) })
        }

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
            sessionPartWriter,
        )
    }
}

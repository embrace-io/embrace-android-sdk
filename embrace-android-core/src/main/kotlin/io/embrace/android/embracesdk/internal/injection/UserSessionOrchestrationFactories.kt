package io.embrace.android.embracesdk.internal.injection

import android.content.Context
import io.embrace.android.embracesdk.internal.arch.InstrumentationRegistry
import io.embrace.android.embracesdk.internal.arch.datasource.TelemetryDestination
import io.embrace.android.embracesdk.internal.arch.startup.StartupClassifier
import io.embrace.android.embracesdk.internal.arch.state.ProcessStateTracker
import io.embrace.android.embracesdk.internal.capture.metadata.MetadataService
import io.embrace.android.embracesdk.internal.capture.session.UserSessionPropertiesService
import io.embrace.android.embracesdk.internal.capture.user.UserService
import io.embrace.android.embracesdk.internal.clock.Clock
import io.embrace.android.embracesdk.internal.config.ConfigService
import io.embrace.android.embracesdk.internal.delivery.caching.PayloadCachingService
import io.embrace.android.embracesdk.internal.delivery.intake.IntakeService
import io.embrace.android.embracesdk.internal.delivery.storage.CachedLogEnvelopeStore
import io.embrace.android.embracesdk.internal.delivery.storage.StorageLocation
import io.embrace.android.embracesdk.internal.delivery.storage.asFile
import io.embrace.android.embracesdk.internal.envelope.log.LogEnvelopeSource
import io.embrace.android.embracesdk.internal.envelope.metadata.EnvelopeMetadataSource
import io.embrace.android.embracesdk.internal.envelope.resource.EnvelopeResourceSource
import io.embrace.android.embracesdk.internal.envelope.session.SessionPartEnvelopeSource
import io.embrace.android.embracesdk.internal.instrumentation.crash.ndk.NativeCrashService
import io.embrace.android.embracesdk.internal.logging.ErrorHandler
import io.embrace.android.embracesdk.internal.logs.LogLimitingService
import io.embrace.android.embracesdk.internal.otel.spans.SpanRepository
import io.embrace.android.embracesdk.internal.resurrection.MultiFileDeadPartResurrector
import io.embrace.android.embracesdk.internal.resurrection.SessionPartReader
import io.embrace.android.embracesdk.internal.resurrection.SessionPartResurrector
import io.embrace.android.embracesdk.internal.session.UserSessionMetadataStore
import io.embrace.android.embracesdk.internal.session.UserSessionRestoreDecision
import io.embrace.android.embracesdk.internal.session.id.SessionIdsProvider
import io.embrace.android.embracesdk.internal.session.id.SessionPartTracker
import io.embrace.android.embracesdk.internal.session.message.PayloadFactoryImpl
import io.embrace.android.embracesdk.internal.session.message.PayloadMessageCollatorImpl
import io.embrace.android.embracesdk.internal.session.orchestrator.OrchestratorBoundaryDelegate
import io.embrace.android.embracesdk.internal.session.orchestrator.PayloadStore
import io.embrace.android.embracesdk.internal.session.orchestrator.SessionOrchestrator
import io.embrace.android.embracesdk.internal.session.orchestrator.SessionOrchestratorImpl
import io.embrace.android.embracesdk.internal.session.orchestrator.SessionPartSpanAttrPopulatorImpl
import io.embrace.android.embracesdk.internal.session.orchestrator.SessionPartWriterImpl
import io.embrace.android.embracesdk.internal.session.persistence.SessionPartDirectoryStore
import io.embrace.android.embracesdk.internal.session.persistence.SessionPartWriteTracker
import io.embrace.android.embracesdk.internal.session.persistence.SessionReconstructionService
import io.embrace.android.embracesdk.internal.spans.CurrentSessionPartSpan
import io.embrace.android.embracesdk.internal.store.KeyValueStore
import io.embrace.android.embracesdk.internal.store.OrdinalStore
import io.embrace.android.embracesdk.internal.telemetry.InternalTelemetryService
import io.embrace.android.embracesdk.internal.utils.EmbTrace
import io.embrace.android.embracesdk.internal.utils.UuidSource
import io.embrace.android.embracesdk.internal.worker.BackgroundWorker

fun createSessionPartReader(
    context: Context,
    clock: Clock,
    errorHandler: ErrorHandler,
    configService: ConfigService,
    sessionPersistenceWorker: BackgroundWorker,
    intakeService: IntakeService,
    cachedLogEnvelopeStore: CachedLogEnvelopeStore,
    nativeCrashServiceProvider: () -> NativeCrashService?,
    restoreDecisionProvider: () -> UserSessionRestoreDecision?,
    processIdProvider: () -> String,
): SessionPartReader {
    val sessionsDir = EmbTrace.trace("sessions-dir-init") {
        StorageLocation.SESSION_SPLIT.asFile(
            errorHandler = errorHandler,
            rootDirSupplier = { context.filesDir },
            fallbackDirSupplier = { context.cacheDir },
        )
    }
    val sessionPartDirectoryStore = EmbTrace.trace("session-part-directory-store-init") {
        SessionPartDirectoryStore(sessionsDir, sessionPersistenceWorker, clock, errorHandler)
    }
    val sessionPartWriteTracker = EmbTrace.trace("session-part-write-tracker-init") {
        SessionPartWriteTracker()
    }
    return SessionPartReader(
        sessionsDir = sessionsDir,
        directoryStore = sessionPartDirectoryStore,
        reconstructionService = SessionReconstructionService(sessionsDir, errorHandler),
        intakeService = intakeService,
        writeTracker = sessionPartWriteTracker,
        deadPartResurrector = MultiFileDeadPartResurrector(
            resurrector = SessionPartResurrector(cachedLogEnvelopeStore),
            nativeCrashServiceProvider = nativeCrashServiceProvider,
            restoreDecisionProvider = restoreDecisionProvider,
        ),
        processIdProvider = processIdProvider,
        configService = configService,
        errorHandler = errorHandler,
    )
}

@Suppress("LongParameterList")
fun createSessionOrchestrator(
    clock: Clock,
    errorHandler: ErrorHandler,
    uuidSource: UuidSource,
    startupClassifier: StartupClassifier,
    telemetryService: InternalTelemetryService,
    configService: ConfigService,
    processStateTracker: ProcessStateTracker,
    sessionPartTracker: SessionPartTracker,
    sessionIdsProvider: SessionIdsProvider,
    userService: UserService,
    userSessionPropertiesService: UserSessionPropertiesService,
    telemetryDestination: TelemetryDestination,
    experimentRecordsProvider: () -> String?,
    currentSessionPartSpan: CurrentSessionPartSpan,
    spanRepository: SpanRepository,
    processIdentifier: String,
    sessionPartEnvelopeSource: SessionPartEnvelopeSource,
    logEnvelopeSource: LogEnvelopeSource,
    metadataService: MetadataService,
    resourceSource: EnvelopeResourceSource,
    envelopeMetadataSource: EnvelopeMetadataSource,
    payloadStore: PayloadStore?,
    payloadCachingService: PayloadCachingService?,
    instrumentationRegistry: InstrumentationRegistry,
    startupDurationProvider: () -> Long?,
    appVersionStartupCounterProvider: () -> Int?,
    logLimitingService: LogLimitingService,
    ordinalStore: OrdinalStore,
    store: KeyValueStore,
    backgroundWorker: BackgroundWorker,
    sessionPersistenceWorker: BackgroundWorker,
    sessionReadWorker: BackgroundWorker,
    sessionPartReader: SessionPartReader?,
): SessionOrchestrator {
    val payloadMessageCollator = PayloadMessageCollatorImpl(
        sessionPartEnvelopeSource,
        currentSessionPartSpan,
        sessionIdsProvider,
    )

    val payloadFactory = PayloadFactoryImpl(
        payloadMessageCollator,
        logEnvelopeSource,
        configService,
        errorHandler,
    )

    val boundaryDelegate = OrchestratorBoundaryDelegate(userSessionPropertiesService)

    val sessionPartSpanAttrPopulator = SessionPartSpanAttrPopulatorImpl(
        telemetryDestination,
        startupDurationProvider,
        appVersionStartupCounterProvider,
        logLimitingService,
        metadataService,
        processIdentifier,
        experimentRecordsProvider,
    )

    val sessionPartWriter: SessionPartWriterImpl? = sessionPartReader?.let { reader ->
        SessionPartWriterImpl(
            reader.sessionsDir,
            sessionPersistenceWorker,
            configService,
            uuidSource,
            clock,
            errorHandler,
            resourceSource,
            envelopeMetadataSource,
            currentSessionPartSpan,
            spanRepository::getActiveEmbraceSpans,
            telemetryService,
            reader.directoryStore,
            reader.writeTracker,
            onWritesComplete = {
                sessionReadWorker.submit { reader.readPersistedSessionParts() }
            },
        ).also { writer ->
            userService.addUserInfoListener(writer::onMetadataChanged)
            spanRepository.addCompletedOtelSpansListener(writer::onSpanCompleted)
            spanRepository.addSpanChangeListener { span ->
                writer.onSpanSnapshotChanged(span)
            }
        }
    }

    return SessionOrchestratorImpl(
        processStateTracker,
        payloadFactory,
        clock,
        configService,
        sessionPartTracker,
        boundaryDelegate,
        payloadStore,
        payloadCachingService,
        instrumentationRegistry,
        telemetryDestination,
        sessionPartSpanAttrPopulator,
        ordinalStore,
        store,
        UserSessionMetadataStore(store),
        errorHandler,
        backgroundWorker,
        uuidSource,
        startupClassifier,
        sessionPartWriter,
    ).apply { attach() }
}

package io.embrace.android.embracesdk.internal.injection

import android.app.ActivityManager
import android.app.Application
import android.content.Context
import io.embrace.android.embracesdk.internal.SystemInfo
import io.embrace.android.embracesdk.internal.arch.InstrumentationArgs
import io.embrace.android.embracesdk.internal.arch.InstrumentationRegistry
import io.embrace.android.embracesdk.internal.arch.InstrumentationRegistryImpl
import io.embrace.android.embracesdk.internal.arch.datasource.TelemetryDestination
import io.embrace.android.embracesdk.internal.arch.destination.TelemetryDestinationImpl
import io.embrace.android.embracesdk.internal.arch.navigation.NavigationTrackingService
import io.embrace.android.embracesdk.internal.arch.startup.StartupClassifier
import io.embrace.android.embracesdk.internal.arch.startup.StartupClassifierImpl
import io.embrace.android.embracesdk.internal.arch.state.ProcessStateTracker
import io.embrace.android.embracesdk.internal.capture.connectivity.NetworkConnectivityService
import io.embrace.android.embracesdk.internal.capture.crumbs.BreadcrumbDataSource
import io.embrace.android.embracesdk.internal.capture.experiment.ExperimentTrackingService
import io.embrace.android.embracesdk.internal.capture.metadata.MetadataService
import io.embrace.android.embracesdk.internal.capture.metadata.RnBundleIdTracker
import io.embrace.android.embracesdk.internal.capture.session.UserSessionPropertiesService
import io.embrace.android.embracesdk.internal.capture.user.UserService
import io.embrace.android.embracesdk.internal.clock.Clock
import io.embrace.android.embracesdk.internal.config.ConfigService
import io.embrace.android.embracesdk.internal.config.behavior.SensitiveKeysBehavior
import io.embrace.android.embracesdk.internal.config.instrumented.schema.InstrumentedConfig
import io.embrace.android.embracesdk.internal.delivery.StoredTelemetryMetadata
import io.embrace.android.embracesdk.internal.delivery.caching.PayloadCachingService
import io.embrace.android.embracesdk.internal.delivery.debug.DeliveryTracer
import io.embrace.android.embracesdk.internal.delivery.execution.RequestExecutionService
import io.embrace.android.embracesdk.internal.delivery.intake.IntakeService
import io.embrace.android.embracesdk.internal.delivery.intake.IntakeServiceImpl
import io.embrace.android.embracesdk.internal.delivery.scheduling.SchedulingService
import io.embrace.android.embracesdk.internal.delivery.scheduling.SchedulingServiceImpl
import io.embrace.android.embracesdk.internal.delivery.storage.CachedLogEnvelopeStore
import io.embrace.android.embracesdk.internal.delivery.storage.PayloadStorageService
import io.embrace.android.embracesdk.internal.delivery.storage.PayloadStorageServiceImpl
import io.embrace.android.embracesdk.internal.delivery.storage.StorageLocation
import io.embrace.android.embracesdk.internal.envelope.log.LogEnvelopeSource
import io.embrace.android.embracesdk.internal.envelope.metadata.EnvelopeMetadataSource
import io.embrace.android.embracesdk.internal.envelope.metadata.EnvelopeMetadataSourceImpl
import io.embrace.android.embracesdk.internal.envelope.metadata.ObservableHostedSdkVersionInfo
import io.embrace.android.embracesdk.internal.envelope.resource.EnvelopeResourceSource
import io.embrace.android.embracesdk.internal.envelope.session.SessionPartEnvelopeSource
import io.embrace.android.embracesdk.internal.instrumentation.crash.CrashFileMarker
import io.embrace.android.embracesdk.internal.instrumentation.crash.CrashFileMarkerImpl
import io.embrace.android.embracesdk.internal.instrumentation.crash.LastRunCrashVerifier
import io.embrace.android.embracesdk.internal.instrumentation.crash.ndk.NativeCrashDataSource
import io.embrace.android.embracesdk.internal.instrumentation.startup.AppStartupDataCollector
import io.embrace.android.embracesdk.internal.instrumentation.startup.SdkInitResourceUsageTracker
import io.embrace.android.embracesdk.internal.instrumentation.startup.StartupService
import io.embrace.android.embracesdk.internal.instrumentation.startup.StartupTracker
import io.embrace.android.embracesdk.internal.instrumentation.startup.activity.UiLoadDataListener
import io.embrace.android.embracesdk.internal.instrumentation.startup.createAppStartupDataCollector
import io.embrace.android.embracesdk.internal.instrumentation.startup.createStartupService
import io.embrace.android.embracesdk.internal.instrumentation.startup.createStartupTracker
import io.embrace.android.embracesdk.internal.instrumentation.startup.createUiLoadDataListener
import io.embrace.android.embracesdk.internal.instrumentation.thread.blockage.ThreadBlockageService
import io.embrace.android.embracesdk.internal.instrumentation.thread.blockage.createThreadBlockageService
import io.embrace.android.embracesdk.internal.logging.ErrorHandler
import io.embrace.android.embracesdk.internal.logs.LogLimitingService
import io.embrace.android.embracesdk.internal.logs.LogLimitingServiceImpl
import io.embrace.android.embracesdk.internal.logs.LogOrchestrator
import io.embrace.android.embracesdk.internal.logs.LogService
import io.embrace.android.embracesdk.internal.logs.LogServiceImpl
import io.embrace.android.embracesdk.internal.logs.attachments.AttachmentService
import io.embrace.android.embracesdk.internal.otel.config.OtelSdkConfig
import io.embrace.android.embracesdk.internal.otel.impl.EmbClock
import io.embrace.android.embracesdk.internal.otel.logs.LogSink
import io.embrace.android.embracesdk.internal.otel.logs.LogSinkImpl
import io.embrace.android.embracesdk.internal.otel.sdk.DataValidator
import io.embrace.android.embracesdk.internal.otel.sdk.OtelSdkWrapper
import io.embrace.android.embracesdk.internal.otel.spans.EmbraceSpanFactory
import io.embrace.android.embracesdk.internal.otel.spans.EmbraceSpanFactoryImpl
import io.embrace.android.embracesdk.internal.otel.spans.SpanRepository
import io.embrace.android.embracesdk.internal.otel.spans.SpanService
import io.embrace.android.embracesdk.internal.otel.spans.SpanServiceImpl
import io.embrace.android.embracesdk.internal.resurrection.PayloadResurrectionService
import io.embrace.android.embracesdk.internal.resurrection.SessionPartReader
import io.embrace.android.embracesdk.internal.serialization.PlatformSerializer
import io.embrace.android.embracesdk.internal.session.id.SessionIdsProvider
import io.embrace.android.embracesdk.internal.session.id.SessionPartTracker
import io.embrace.android.embracesdk.internal.session.lifecycle.createLifecycleTracker
import io.embrace.android.embracesdk.internal.session.orchestrator.PayloadStore
import io.embrace.android.embracesdk.internal.session.orchestrator.SessionOrchestrator
import io.embrace.android.embracesdk.internal.spans.CurrentSessionPartSpan
import io.embrace.android.embracesdk.internal.spans.TracingApiDelegate
import io.embrace.android.embracesdk.internal.storage.EmbraceStorageService
import io.embrace.android.embracesdk.internal.storage.StatFsAvailabilityChecker
import io.embrace.android.embracesdk.internal.storage.StorageService
import io.embrace.android.embracesdk.internal.store.KeyValueStore
import io.embrace.android.embracesdk.internal.store.Ordinal
import io.embrace.android.embracesdk.internal.store.OrdinalStore
import io.embrace.android.embracesdk.internal.store.OrdinalStoreImpl
import io.embrace.android.embracesdk.internal.telemetry.InternalTelemetryService
import io.embrace.android.embracesdk.internal.utils.BuildVersionChecker
import io.embrace.android.embracesdk.internal.utils.EmbTrace
import io.embrace.android.embracesdk.internal.utils.UuidSource
import io.embrace.android.embracesdk.internal.utils.VersionChecker
import io.embrace.android.embracesdk.internal.utils.event.EventBus
import io.embrace.android.embracesdk.internal.worker.PriorityWorker
import io.embrace.android.embracesdk.internal.worker.Worker
import io.embrace.android.embracesdk.spans.TracingApi
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

/**
 * Constructs objects used by the initialized SDK. Only constructed once [ConfigLoadServices] has
 * confirmed the SDK is not disabled. Construction has no side effects: [start] must be called afterwards.
 *
 * [SdkOverrides] are test seams and are null in production. Where they are null the real construction
 * below runs instead; because [resolve] and [init] are inlined that costs no lambda allocation on the default path.
 */
internal class InitializedModuleGraph(
    context: Context,
    preStartServices: PreStartServices,
    configLoadServices: ConfigLoadServices,
    val sdkStartTimeMs: Long,
    val sdkInitResourceUsageTracker: SdkInitResourceUsageTracker,
    overrides: SdkOverrides? = null,
) {

    val clock: Clock = preStartServices.clock
    val errorHandler: ErrorHandler = preStartServices.errorHandler
    val systemInfo: SystemInfo = preStartServices.systemInfo
    val telemetryService: InternalTelemetryService = preStartServices.telemetryService
    val jsonSerializer: PlatformSerializer = configLoadServices.jsonSerializer
    val instrumentedConfig: InstrumentedConfig = configLoadServices.instrumentedConfig
    val okHttpClient: Lazy<OkHttpClient> = configLoadServices.okHttpClient
    val uuidSource: UuidSource = configLoadServices.uuidSource

    val startupClassifier: StartupClassifier = init(name = "init-module", recordDuration = true) {
        StartupClassifierImpl()
    }

    val workerThreadPool: WorkerThreadPool = init("worker-thread-pool") { configLoadServices.workerThreadPool }
    val configService: ConfigService = init("config-service") { configLoadServices.configService }

    private val otelSignalContext: OtelSignalContext = init("otel-signal-context") {
        OtelSignalContext(
            sessionIdsProvider = { sessionIdsProvider },
            userServiceProvider = { userService.value },
            sessionPartTrackerProvider = { sessionPartTracker },
            processStateTrackerProvider = { processStateTracker },
            experimentTrackingServiceProvider = { experimentTrackingService.value },
            userSessionPropertiesServiceProvider = { userSessionPropertiesService },
            instrumentationRegistryProvider = { instrumentationRegistry },
        )
    }

    private val openTelemetryClock: EmbClock = init("open-telemetry-clock") { EmbClock(clock) }

    private val sensitiveKeysBehavior: SensitiveKeysBehavior = init("sensitive-keys-behavior") {
        SensitiveKeysBehavior(configService.config.sensitiveKeys)
    }

    val spanRepository: SpanRepository = init("span-repository") {
        overrides?.spanRepository.resolve { SpanRepository() }
    }

    val logSink: LogSink = init("log-sink") { LogSinkImpl() }

    val otelSdkConfig: Lazy<OtelSdkConfig> = lazy {
        EmbTrace.trace("otel-sdk-config-init") {
            overrides?.otelSdkConfig.resolve {
                createOtelSdkConfig(
                    spanRepository = spanRepository,
                    logSink = logSink,
                    instrumentedConfig = instrumentedConfig,
                    systemInfo = systemInfo,
                    uuidSource = uuidSource,
                    errorHandler = errorHandler,
                    externalOtelConfig = preStartServices.externalOtelConfig,
                    sessionIdsProvider = otelSignalContext::getSessionIdsProvider,
                    userIdProvider = otelSignalContext::getUserId,
                    eventMetadataProvider = otelSignalContext::getEventMetadata,
                )
            }
        }
    }

    val otelSdkWrapper: Lazy<OtelSdkWrapper> = lazy {
        EmbTrace.trace("otel-sdk-wrapper-init") {
            overrides?.otelSdkWrapper.resolve {
                OtelSdkWrapper(
                    otelClock = openTelemetryClock,
                    configuration = otelSdkConfig.value,
                    spanServiceProvider = { spanService },
                    errorHandler = errorHandler,
                    useKotlinSdk = configService.otelBehavior.shouldUseKotlinSdk(),
                )
            }
        }
    }

    private val dataValidator: DataValidator = init("data-validator") {
        DataValidator(
            bypassValidation = configService::isOnlyUsingOtelExporters,
            telemetryService = telemetryService,
        )
    }

    val currentSessionPartSpan: CurrentSessionPartSpan = init("current-session-part-span-impl") {
        createCurrentSessionPartSpan(
            openTelemetryClock = openTelemetryClock,
            telemetryService = telemetryService,
            spanRepository = spanRepository,
            tracerSupplier = { otelSdkWrapper.value.sdkTracer },
            openTelemetrySupplier = { otelSdkWrapper.value.openTelemetryKotlin },
            embraceSpanFactorySupplier = { embraceSpanFactory },
            uuidSource = uuidSource,
            otelBehaviorSupplier = { configService.otelBehavior },
            customBreadcrumbLimitSupplier = { configService.config.breadcrumb.customLimit },
        )
    }

    private val embraceSpanFactory: EmbraceSpanFactory = init("embrace-span-factory") {
        EmbraceSpanFactoryImpl(
            openTelemetryClock = openTelemetryClock,
            spanRepository = spanRepository,
            dataValidator = dataValidator,
            stopCallback = currentSessionPartSpan::spanStopCallback,
            redactionFunction = createRedactionFunction(sensitiveKeysBehavior),
            telemetryService = telemetryService,
        )
    }

    val spanService: SpanService by lazy {
        EmbTrace.trace("span-service-init") {
            SpanServiceImpl(
                spanRepository = spanRepository,
                canStartNewSpan = currentSessionPartSpan::canStartNewSpan,
                dataValidator = dataValidator,
                embraceSpanFactory = embraceSpanFactory,
                tracer = otelSdkWrapper.value.sdkTracer,
                openTelemetry = otelSdkWrapper.value.openTelemetryKotlin,
            )
        }
    }

    val tracingApi: TracingApi by lazy {
        EmbTrace.trace("tracing-api-init") { TracingApiDelegate(spanService) }
    }

    val appContext: Context = init("context") {
        overrides?.appContext.resolve { context.applicationContextOrSelf() }
    }

    val application: Application
        get() = appContext as Application

    val store: KeyValueStore by configLoadServices.keyValueStore

    val ordinalStore: OrdinalStore by lazy {
        EmbTrace.trace("ordinal-store-init") { OrdinalStoreImpl(store) }
    }

    val eventBus: EventBus by lazy {
        EmbTrace.trace("event-bus-init") { overrides?.eventBus.resolve { EventBus(errorHandler) } }
    }

    val appVersionStartupCounter: Int by lazy {
        EmbTrace.trace("app-version-startup-counter-init") {
            ordinalStore.incrementAndGet(
                ordinal = Ordinal.APP_VERSION_STARTUP,
                scope = instrumentedConfig.project.getVersionName() ?: "UNKNOWN",
            )
        }
    }

    val processStateTracker: ProcessStateTracker = init(name = "essential-service-module", recordDuration = true) {
        createProcessStateTracker(
            errorHandler = errorHandler,
            lifecycleTracker = overrides?.lifecycleTracker.resolve {
                createLifecycleTracker(configService, application, context)
            },
        )
    }

    val navigationTrackingService: NavigationTrackingService = init("navigation-tracking-service") {
        createNavigationTrackingService()
    }

    val networkConnectivityService: NetworkConnectivityService = init("network-connectivity-service") {
        overrides?.networkConnectivityService.resolve {
            createNetworkConnectivityService(
                context = appContext,
                configService = configService,
                worker = workerThreadPool.backgroundWorker(Worker.Background.NonIoRegWorker),
                errorHandler = errorHandler,
            )
        }
    }

    val sessionPartTracker: SessionPartTracker = init("session-part-tracker") {
        createSessionPartTracker(appContext, errorHandler)
    }

    val sessionIdsProvider: SessionIdsProvider = init("session-ids-provider") {
        overrides?.sessionIdsProvider.resolve { createSessionIdsProvider({ sessionOrchestrator }, sessionPartTracker) }
    }

    val telemetryDestination: TelemetryDestination = init("telemetry-destination") {
        TelemetryDestinationImpl(
            clock = clock,
            spanService = spanService,
            loggerSupplier = { otelSdkWrapper.value.sdkLogger },
            currentSessionPartSpan = currentSessionPartSpan,
        )
    }

    val userSessionPropertiesService: UserSessionPropertiesService = init("user-session-properties-service") {
        createUserSessionPropertiesService(
            store = lazy { store },
            configService = configService,
            destination = telemetryDestination,
            telemetryService = telemetryService,
        )
    }

    val userService: Lazy<UserService> = lazy {
        EmbTrace.trace("user-service-init") {
            createUserService(store, clock, errorHandler)
        }
    }

    val experimentTrackingService: Lazy<ExperimentTrackingService> = lazy {
        EmbTrace.trace("experiment-tracking-service-init") {
            overrides?.experimentTrackingService.resolve {
                createExperimentTrackingService(configService, telemetryService, telemetryDestination)
            }
        }
    }

    val storageService: StorageService = init("storage-service") {
        EmbraceStorageService(
            appContext,
            telemetryService,
            StatFsAvailabilityChecker(appContext),
        )
    }

    val instrumentationRegistry: InstrumentationRegistry = init("instrumentation-registry") {
        overrides?.instrumentationRegistry.resolve { InstrumentationRegistryImpl(errorHandler) }
    }

    val instrumentationArgs: InstrumentationArgs = init(name = "instrumentation-module", recordDuration = true) {
        createInstrumentationArgs(
            configService = configService,
            destination = telemetryDestination,
            errorHandler = errorHandler,
            clock = clock,
            context = appContext,
            application = application,
            storeProvider = { store },
            serializer = jsonSerializer,
            uuidSource = uuidSource,
            ordinalStore = ordinalStore,
            processIdentifier = otelSdkConfig.value.processIdentifier,
            processStateTracker = processStateTracker,
            navigationTrackingService = navigationTrackingService,
            telemetryService = telemetryService,
            workerThreadPool = workerThreadPool,
            sessionPartTracker = sessionPartTracker,
            userSessionPropertiesService = userSessionPropertiesService,
            userSessionIdsProvider = { sessionIdsProvider.getCurrentUserSessionId() },
            activeSessionIdsProvider = { sessionIdsProvider.getActiveSessionIds() },
            crashMarkerFileProvider = { storageService.getFileForWrite("embrace_crash_marker") },
            eventBus = eventBus,
        )
    }

    val breadcrumbDataSource: Lazy<BreadcrumbDataSource?> = lazy {
        instrumentationRegistry.add {
            BreadcrumbDataSource(instrumentationArgs)
        }
    }

    val crashMarker: CrashFileMarker = init("crash-marker") {
        CrashFileMarkerImpl(
            lazy { storageService.getFileForWrite(CrashFileMarkerImpl.CRASH_MARKER_FILE_NAME) },
        )
    }

    val lastRunCrashVerifier: LastRunCrashVerifier = init("last-run-crash-verifier") {
        LastRunCrashVerifier(crashMarker)
    }

    private val versionChecker: VersionChecker = overrides?.versionChecker.resolve { BuildVersionChecker }

    val startupService: StartupService = init("startup-service") {
        createStartupService(
            destination = instrumentationArgs.destination,
            appVersionStartupCounterProvider = { appVersionStartupCounter },
        )
    }

    val appStartupDataCollector: AppStartupDataCollector = init("app-startup-data-collector") {
        val instrumentationArgs = instrumentationArgs
        createAppStartupDataCollector(
            clock = clock,
            errorHandler = errorHandler,
            destination = instrumentationArgs.destination,
            configService = configService,
            startupService = startupService,
            startupClassifier = startupClassifier,
            versionChecker = versionChecker,
            activityManager = instrumentationArgs.systemService<ActivityManager>(Context.ACTIVITY_SERVICE),
            backgroundWorker = workerThreadPool.backgroundWorker(Worker.Background.NonIoRegWorker),
        )
    }

    val uiLoadDataListener: UiLoadDataListener? = init("ui-load-data-listener") {
        createUiLoadDataListener(
            destination = instrumentationArgs.destination,
            configService = configService,
            versionChecker = versionChecker,
        )
    }

    val startupTracker: StartupTracker = init("startup-tracker") {
        createStartupTracker(
            clock = clock,
            errorHandler = errorHandler,
            configService = configService,
            appStartupDataCollector = appStartupDataCollector,
            uiLoadDataListener = uiLoadDataListener,
            versionChecker = versionChecker,
        )
    }

    private val deliveryTracer: DeliveryTracer? = overrides?.deliveryTracer.resolve { null }

    private val processIdProvider: () -> String = { otelSdkConfig.value.processIdentifier }

    private val dataPersistenceWorker: PriorityWorker<StoredTelemetryMetadata>? = init("data-persistence-worker") {
        when {
            !configService.isOnlyUsingOtelExporters() -> workerThreadPool.priorityWorker(Worker.Priority.DataPersistenceWorker)
            else -> null
        }
    }

    val payloadStorageService: PayloadStorageService? = init(name = "delivery-module", recordDuration = true) {
        dataPersistenceWorker?.let { worker ->
            overrides?.payloadStorageService.resolve {
                createPayloadStorageService(
                    location = StorageLocation.PAYLOAD,
                    context = appContext,
                    worker = worker,
                    processIdProvider = processIdProvider,
                    errorHandler = errorHandler,
                    clock = clock,
                    deliveryTracer = deliveryTracer,
                    limit = overrides?.payloadStorageLimit.resolve { PayloadStorageServiceImpl.DEFAULT_STORAGE_LIMIT },
                )
            }
        }
    }

    val cacheStorageService: PayloadStorageService? = init("cache-storage-service") {
        dataPersistenceWorker?.let { worker ->
            overrides?.cacheStorageService.resolve {
                createPayloadStorageService(
                    location = StorageLocation.CACHE,
                    context = appContext,
                    worker = worker,
                    processIdProvider = processIdProvider,
                    errorHandler = errorHandler,
                    clock = clock,
                    deliveryTracer = deliveryTracer,
                )
            }
        }
    }

    private val requestExecutionService: RequestExecutionService? = init("request-execution-service") {
        if (dataPersistenceWorker != null) {
            overrides?.requestExecutionService.resolve {
                createRequestExecutionService(
                    configService = configService,
                    instrumentedConfig = instrumentedConfig,
                    okHttpClient = okHttpClient,
                    errorHandler = errorHandler,
                    deliveryTracer = deliveryTracer,
                )
            }
        } else {
            null
        }
    }

    val schedulingService: SchedulingService? = init("scheduling-service") {
        if (payloadStorageService != null && requestExecutionService != null) {
            SchedulingServiceImpl(
                payloadStorageService,
                requestExecutionService,
                workerThreadPool.backgroundWorker(Worker.Background.DeliverySchedulingWorker),
                workerThreadPool.backgroundWorker(Worker.Background.HttpRequestWorker),
                clock,
                errorHandler,
                deliveryTracer,
            )
        } else {
            null
        }
    }

    val intakeService: IntakeService? = init("intake-service") {
        dataPersistenceWorker?.let { worker ->
            if (schedulingService != null && payloadStorageService != null && cacheStorageService != null) {
                overrides?.intakeService.resolve {
                    IntakeServiceImpl(
                        schedulingService,
                        payloadStorageService,
                        cacheStorageService,
                        errorHandler,
                        jsonSerializer,
                        worker,
                        deliveryTracer,
                    )
                }
            } else {
                null
            }
        }
    }

    val payloadStore: PayloadStore? = init("payload-store") {
        intakeService?.let {
            createPayloadStore(
                intakeService = it,
                clock = clock,
                processIdProvider = processIdProvider,
                uuidSource = uuidSource,
                sessionIdsProvider = sessionIdsProvider::getActiveSessionIds,
            )
        }
    }

    val payloadCachingService: PayloadCachingService? = init("payload-caching-service") {
        payloadStore?.let {
            createPayloadCachingService(
                worker = workerThreadPool.backgroundWorker(Worker.Background.PeriodicCacheWorker),
                errorHandler = errorHandler,
                periodicCacheIntervalMs = configService.otelBehavior.getPeriodicCacheIntervalMs(),
                clock = clock,
                sessionIdsProvider = sessionIdsProvider,
                payloadStore = it,
                deliveryTracer = deliveryTracer,
            )
        }
    }

    val cachedLogEnvelopeStore: CachedLogEnvelopeStore? = init("cached-log-envelope-store") {
        dataPersistenceWorker?.let { worker ->
            createCachedLogEnvelopeStore(
                context = appContext,
                worker = worker,
                errorHandler = errorHandler,
                serializer = jsonSerializer,
                clock = clock,
            )
        }
    }

    val threadBlockageService: ThreadBlockageService? = init("thread-blockage-service") {
        // An override that returns null disables the service: it is not replaced by the real construction
        overrides?.threadBlockageService.resolve { createThreadBlockageService(instrumentationArgs) }
    }

    val rnBundleIdTracker: Lazy<RnBundleIdTracker> = lazy {
        EmbTrace.trace("rn-bundle-id-tracker-init") {
            createRnBundleIdTracker(
                context = appContext,
                configService = configService,
                store = store,
                worker = workerThreadPool.backgroundWorker(Worker.Background.NonIoRegWorker),
            )
        }
    }

    val hostedSdkVersionInfo: ObservableHostedSdkVersionInfo = init("observable-hosted-sdk-version-info") {
        createHostedSdkVersionInfo(configService.appFramework, store)
    }

    val resourceSource: EnvelopeResourceSource = init(name = "payload-source-module", recordDuration = true) {
        createEnvelopeResourceSource(
            context = appContext,
            configService = configService,
            worker = workerThreadPool.backgroundWorker(Worker.Background.NonIoRegWorker),
            systemInfo = systemInfo,
            errorHandler = errorHandler,
            hostedSdkVersionInfo = hostedSdkVersionInfo,
            rnBundleIdTracker = rnBundleIdTracker,
            useKotlinSdkProvider = { otelSdkWrapper.value.useKotlinSdk },
        )
    }

    val envelopeMetadataSource: EnvelopeMetadataSource = init("envelope-metadata-source") {
        EnvelopeMetadataSourceImpl { userService.value.getUserInfo() }
    }

    val sessionPartEnvelopeSource: SessionPartEnvelopeSource = init("session-part-envelope-source") {
        createSessionPartEnvelopeSource(
            symbolMap = configService.nativeSymbolMap,
            currentSessionPartSpan = currentSessionPartSpan,
            spanRepository = spanRepository,
            otelPayloadMapper = threadBlockageService,
            processStateTracker = processStateTracker,
            clock = clock,
            errorHandler = errorHandler,
            envelopeMetadataSource = envelopeMetadataSource,
            resourceSource = resourceSource,
        )
    }

    val logEnvelopeSource: LogEnvelopeSource = init("log-envelope-source") {
        createLogEnvelopeSource(
            logSink = logSink,
            envelopeMetadataSource = envelopeMetadataSource,
            resourceSource = resourceSource,
            cachedLogEnvelopeStore = cachedLogEnvelopeStore,
        )
    }

    val metadataService: MetadataService = init("metadata-service") {
        createMetadataService(
            resourceSource = resourceSource,
            context = appContext,
            configService = configService,
            store = store,
            clock = clock,
            worker = workerThreadPool.backgroundWorker(Worker.Background.NonIoRegWorker),
        )
    }

    val payloadResurrectionService: PayloadResurrectionService? = init("payload-resurrection-service") {
        intakeService?.let { intake ->
            if (payloadStorageService != null && cacheStorageService != null && cachedLogEnvelopeStore != null) {
                createPayloadResurrectionService(
                    intakeService = intake,
                    payloadStorageService = payloadStorageService,
                    cacheStorageService = cacheStorageService,
                    cachedLogEnvelopeStore = cachedLogEnvelopeStore,
                    errorHandler = errorHandler,
                    serializer = jsonSerializer,
                )
            } else {
                null
            }
        }
    }

    val logLimitingService: LogLimitingService = init("log-limiting-service") {
        LogLimitingServiceImpl(configService)
    }

    val logOrchestrator: LogOrchestrator = init("log-orchestrator") {
        createLogOrchestrator(
            worker = overrides?.logOrchestratorWorker.resolve {
                workerThreadPool.backgroundWorker(Worker.Background.NonIoRegWorker)
            },
            clock = clock,
            sink = logSink,
            payloadStore = payloadStore,
            logEnvelopeSource = logEnvelopeSource,
        )
    }

    val attachmentService: AttachmentService = init("attachment-service") { AttachmentService() }

    /**
     * Deferred: only reached via the public logging API, never during SDK init.
     */
    val logService: Lazy<LogService> = lazy(LazyThreadSafetyMode.PUBLICATION) {
        LogServiceImpl(
            telemetryDestination,
            configService,
            logLimitingService,
            telemetryService,
        )
    }

    val sessionPartReader: SessionPartReader? = init("session-part-reader") {
        if (intakeService != null && cachedLogEnvelopeStore != null) {
            createSessionPartReader(
                context = appContext,
                clock = clock,
                errorHandler = errorHandler,
                configService = configService,
                sessionPersistenceWorker = workerThreadPool.backgroundWorker(Worker.Background.SessionPersistenceWorker),
                intakeService = intakeService,
                cachedLogEnvelopeStore = cachedLogEnvelopeStore,
                nativeCrashServiceProvider = {
                    instrumentationRegistry.findByType(NativeCrashDataSource::class)
                },
                restoreDecisionProvider = { sessionOrchestrator.userSessionRestoreDecision },
                processIdProvider = processIdProvider,
            )
        } else {
            null
        }
    }

    val sessionOrchestrator: SessionOrchestrator =
        init(name = "user-session-orchestration-module", recordDuration = true) {
            overrides?.sessionOrchestrator.resolve {
                createSessionOrchestrator(
                    clock = clock,
                    errorHandler = errorHandler,
                    uuidSource = uuidSource,
                    startupClassifier = startupClassifier,
                    telemetryService = telemetryService,
                    configService = configService,
                    processStateTracker = processStateTracker,
                    sessionPartTracker = sessionPartTracker,
                    sessionIdsProvider = sessionIdsProvider,
                    userService = userService.value,
                    userSessionPropertiesService = userSessionPropertiesService,
                    telemetryDestination = telemetryDestination,
                    experimentRecordsProvider = { experimentTrackingService.value.getRecords() },
                    currentSessionPartSpan = currentSessionPartSpan,
                    spanRepository = spanRepository,
                    processIdentifier = otelSdkConfig.value.processIdentifier,
                    sessionPartEnvelopeSource = sessionPartEnvelopeSource,
                    logEnvelopeSource = logEnvelopeSource,
                    metadataService = metadataService,
                    resourceSource = resourceSource,
                    envelopeMetadataSource = envelopeMetadataSource,
                    payloadStore = payloadStore,
                    payloadCachingService = payloadCachingService,
                    instrumentationRegistry = instrumentationRegistry,
                    startupDurationProvider = startupService::getSdkStartupDuration,
                    appVersionStartupCounterProvider = startupService::getAppVersionStartupCounter,
                    logLimitingService = logLimitingService,
                    ordinalStore = ordinalStore,
                    store = store,
                    backgroundWorker = workerThreadPool.backgroundWorker(Worker.Background.NonIoRegWorker),
                    sessionPersistenceWorker = workerThreadPool.backgroundWorker(Worker.Background.SessionPersistenceWorker),
                    sessionReadWorker = workerThreadPool.backgroundWorker(Worker.Background.IoRegWorker),
                    sessionPartReader = sessionPartReader,
                )
            }
        }

    /**
     * Performs the side effects of initialization. Must be called once, after construction.
     */
    fun start() {
        EmbTrace.trace(sectionName = "span-service-init", recordDuration = true) {
            currentSessionPartSpan.initializeService(sdkStartTimeMs)
        }
        EmbTrace.trace("storage-telemetry-schedule") {
            workerThreadPool
                .backgroundWorker(Worker.Background.IoRegWorker)
                .schedule<Unit>({ storageService.logStorageTelemetry() }, 1, TimeUnit.MINUTES)
        }
        EmbTrace.trace("otel-signal-context-mark-ready") {
            otelSignalContext.markReady()
        }
    }

    private inline fun <T> init(name: String, recordDuration: Boolean = false, supplier: () -> T): T =
        EmbTrace.trace("$name-init", recordDuration) { supplier() }
}

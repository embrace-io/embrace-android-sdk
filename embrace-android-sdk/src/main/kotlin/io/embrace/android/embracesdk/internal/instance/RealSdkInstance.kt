package io.embrace.android.embracesdk.internal.instance

import android.app.Application
import android.content.Context
import android.util.Log
import io.embrace.android.embracesdk.core.BuildConfig
import io.embrace.android.embracesdk.internal.InternalInterfaceApi
import io.embrace.android.embracesdk.internal.api.BreadcrumbApi
import io.embrace.android.embracesdk.internal.api.ExperimentApi
import io.embrace.android.embracesdk.internal.api.InstrumentationApi
import io.embrace.android.embracesdk.internal.api.LogsApi
import io.embrace.android.embracesdk.internal.api.NetworkRequestApi
import io.embrace.android.embracesdk.internal.api.OTelApi
import io.embrace.android.embracesdk.internal.api.SdkStateApi
import io.embrace.android.embracesdk.internal.api.UserApi
import io.embrace.android.embracesdk.internal.api.UserSessionApi
import io.embrace.android.embracesdk.internal.api.ViewTrackingApi
import io.embrace.android.embracesdk.internal.api.delegate.BreadcrumbApiDelegate
import io.embrace.android.embracesdk.internal.api.delegate.ExperimentApiDelegate
import io.embrace.android.embracesdk.internal.api.delegate.HostedSdkInternalInterfaceImpl
import io.embrace.android.embracesdk.internal.api.delegate.InstrumentationApiDelegate
import io.embrace.android.embracesdk.internal.api.delegate.LogsApiDelegate
import io.embrace.android.embracesdk.internal.api.delegate.NetworkRequestApiDelegate
import io.embrace.android.embracesdk.internal.api.delegate.OTelApiDelegate
import io.embrace.android.embracesdk.internal.api.delegate.SdkStateApiDelegate
import io.embrace.android.embracesdk.internal.api.delegate.UserApiDelegate
import io.embrace.android.embracesdk.internal.api.delegate.UserSessionApiDelegate
import io.embrace.android.embracesdk.internal.api.delegate.ViewTrackingApiDelegate
import io.embrace.android.embracesdk.internal.arch.InstrumentationArgs
import io.embrace.android.embracesdk.internal.arch.InstrumentationProvider
import io.embrace.android.embracesdk.internal.arch.InstrumentationRegistry
import io.embrace.android.embracesdk.internal.arch.datasource.TelemetryDestination
import io.embrace.android.embracesdk.internal.arch.state.ProcessStateTracker
import io.embrace.android.embracesdk.internal.capture.connectivity.NetworkConnectivityService
import io.embrace.android.embracesdk.internal.capture.metadata.MetadataService
import io.embrace.android.embracesdk.internal.clock.Clock
import io.embrace.android.embracesdk.internal.config.ConfigService
import io.embrace.android.embracesdk.internal.delivery.caching.PayloadCachingService
import io.embrace.android.embracesdk.internal.delivery.scheduling.SchedulingService
import io.embrace.android.embracesdk.internal.delivery.storage.PayloadStorageService
import io.embrace.android.embracesdk.internal.delivery.storage.StorageLocation
import io.embrace.android.embracesdk.internal.delivery.storage.asFile
import io.embrace.android.embracesdk.internal.injection.WorkerThreadPool
import io.embrace.android.embracesdk.internal.injection.evictConnections
import io.embrace.android.embracesdk.internal.instrumentation.crash.CrashFileMarker
import io.embrace.android.embracesdk.internal.instrumentation.crash.LastRunCrashVerifier
import io.embrace.android.embracesdk.internal.instrumentation.crash.jvm.JvmCrashDataSource
import io.embrace.android.embracesdk.internal.instrumentation.crash.ndk.NativeCrashDataSource
import io.embrace.android.embracesdk.internal.instrumentation.network.NetworkCaptureDataSource
import io.embrace.android.embracesdk.internal.instrumentation.network.NetworkRequestDataSource
import io.embrace.android.embracesdk.internal.instrumentation.network.NetworkStateDataSource
import io.embrace.android.embracesdk.internal.instrumentation.network.NetworkStatusDataSource
import io.embrace.android.embracesdk.internal.instrumentation.startup.AppStartupDataCollector
import io.embrace.android.embracesdk.internal.instrumentation.startup.ArtOptimizationState
import io.embrace.android.embracesdk.internal.instrumentation.startup.SdkInitResourceUsageTracker
import io.embrace.android.embracesdk.internal.instrumentation.startup.StartupService
import io.embrace.android.embracesdk.internal.instrumentation.startup.StartupTracker
import io.embrace.android.embracesdk.internal.instrumentation.startup.sdkInitEnvironmentAttributes
import io.embrace.android.embracesdk.internal.instrumentation.startup.toSdkInitDurationAttributes
import io.embrace.android.embracesdk.internal.instrumentation.thread.blockage.ThreadBlockageService
import io.embrace.android.embracesdk.internal.logging.ErrorHandler
import io.embrace.android.embracesdk.internal.logging.SdkErrorType
import io.embrace.android.embracesdk.internal.logging.guardSdkCode
import io.embrace.android.embracesdk.internal.logs.LogLimitingService
import io.embrace.android.embracesdk.internal.logs.LogOrchestrator
import io.embrace.android.embracesdk.internal.logs.attachments.AttachmentService
import io.embrace.android.embracesdk.internal.otel.config.OtelSdkConfig
import io.embrace.android.embracesdk.internal.otel.logs.LogSink
import io.embrace.android.embracesdk.internal.otel.spans.SpanRepository
import io.embrace.android.embracesdk.internal.resurrection.PayloadResurrectionService
import io.embrace.android.embracesdk.internal.resurrection.SessionPartReader
import io.embrace.android.embracesdk.internal.session.id.SessionPartTracker
import io.embrace.android.embracesdk.internal.session.orchestrator.PayloadStore
import io.embrace.android.embracesdk.internal.session.orchestrator.SessionOrchestrator
import io.embrace.android.embracesdk.internal.utils.EmbTrace
import io.embrace.android.embracesdk.internal.utils.event.EventBus
import io.embrace.android.embracesdk.internal.worker.Worker
import io.embrace.android.embracesdk.spans.TracingApi
import io.opentelemetry.kotlin.NoopOpenTelemetry
import io.opentelemetry.kotlin.OpenTelemetry
import okhttp3.OkHttpClient
import java.io.File
import java.util.ServiceLoader
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

private const val WORKER_TERMINATION_TIMEOUT_MS = 10_000L

/**
 * Implementation class of the SDK. Embrace.java forms our public API and calls functions in this
 * class.
 *
 * Any non-public APIs or functionality related to the Embrace.java client should ideally be put
 * here instead.
 */
@Suppress("LongParameterList")
internal class RealSdkInstance(
    private val clock: Clock,
    private val errorHandler: ErrorHandler,
    private val primaryAbi: String,
    private val configService: ConfigService,
    private val workerThreadPool: WorkerThreadPool,
    private val okHttpClient: Lazy<OkHttpClient>,
    private val eventBus: EventBus,
    private val appContext: Context,
    private val spanRepository: SpanRepository,
    private val logSink: LogSink,
    private val otelSdkConfig: Lazy<OtelSdkConfig>,
    private val processStateTracker: ProcessStateTracker,
    private val sessionPartTracker: SessionPartTracker,
    private val networkConnectivityService: NetworkConnectivityService,
    private val telemetryDestination: TelemetryDestination,
    private val instrumentationRegistry: InstrumentationRegistry,
    private val instrumentationArgs: InstrumentationArgs,
    private val sessionOrchestrator: SessionOrchestrator,
    private val sessionPartReader: SessionPartReader?,
    private val logOrchestrator: LogOrchestrator,
    private val attachmentService: AttachmentService,
    private val logLimitingService: LogLimitingService,
    private val metadataService: MetadataService,
    private val payloadResurrectionService: PayloadResurrectionService?,
    private val payloadCachingService: PayloadCachingService?,
    private val payloadStore: PayloadStore?,
    private val schedulingService: SchedulingService?,
    private val cacheStorageService: PayloadStorageService?,
    private val threadBlockageService: ThreadBlockageService?,
    private val lastRunCrashVerifier: LastRunCrashVerifier,
    private val crashMarker: CrashFileMarker,
    private val startupTracker: StartupTracker,
    private val startupService: StartupService,
    private val appStartupDataCollector: AppStartupDataCollector,
    private val sdkStartTimeMs: Long,
    private val sdkInitResourceUsageTracker: SdkInitResourceUsageTracker,
    userApiDelegate: UserApiDelegate,
    sessionApiDelegate: UserSessionApiDelegate,
    networkRequestApiDelegate: NetworkRequestApiDelegate,
    logsApiDelegate: LogsApiDelegate,
    hostedSdkInternalInterface: HostedSdkInternalInterfaceImpl,
    viewTrackingApiDelegate: ViewTrackingApiDelegate,
    sdkStateApiDelegate: SdkStateApiDelegate,
    private val otelApiDelegate: OTelApiDelegate,
    breadcrumbApiDelegate: BreadcrumbApiDelegate,
    instrumentationApiDelegate: InstrumentationApiDelegate,
    private val experimentApiDelegate: ExperimentApiDelegate,
    private val tracingApi: TracingApi,
) : SdkInstance,
    LogsApi by logsApiDelegate,
    NetworkRequestApi by networkRequestApiDelegate,
    UserSessionApi by sessionApiDelegate,
    UserApi by userApiDelegate,
    SdkStateApi by sdkStateApiDelegate,
    OTelApi by otelApiDelegate,
    ViewTrackingApi by viewTrackingApiDelegate,
    BreadcrumbApi by breadcrumbApiDelegate,
    InstrumentationApi by instrumentationApiDelegate,
    ExperimentApi by experimentApiDelegate,
    InternalInterfaceApi by hostedSdkInternalInterface,
    TracingApi by tracingApi {

    @Volatile
    private var applicationInitStartMs: Long? = null

    @Volatile
    private var started: Boolean = false

    override val isStarted: Boolean
        get() = started

    override fun getOpenTelemetryKotlin(): OpenTelemetry = if (started) {
        otelApiDelegate.getOpenTelemetryKotlin()
    } else {
        NoopOpenTelemetry
    }

    override fun onPostStart(buffer: BufferingSdkInstance) {
        buffer.drainCompletedSpans(tracingApi)
        buffer.drainExperimentCalls(experimentApiDelegate, experimentApiDelegate::replay)
        // the buffered value was recorded first, so takes precedence
        buffer.applicationInitStartMs?.let { applicationInitStartMs = it }

        // drained last so errors raised by the drains above are captured
        buffer.drainSdkErrors(
            telemetryDestination.takeIf {
                configService.config.dataCaptureEvent.internalExceptionCaptureEnabled
            },
        )
    }

    override fun start(context: Context) {
        try {
            EmbTrace.trace(sectionName = "process-state-tracker-register", recordDuration = true) {
                processStateTracker.register()
            }
            postInit()

            EmbTrace.trace(sectionName = "post-services-setup", recordDuration = true) {
                // not fully initialized, but the SDK shouldn't catastrophically throw after this point,
                // so we allow external calls.
                started = true
                registerListeners()
                loadInstrumentation()
                postLoadInstrumentation()
                triggerPayloadSend()
            }
            markSdkInitComplete(EmbTrace.durationTracker::flush)
        } catch (ignored: Throwable) {
            Log.w("Embrace", "Failed to initialize Embrace SDK", ignored)
        }
    }

    override fun disable() = tearDown(deleteData = true)
    override fun close() = tearDown(deleteData = false)

    /**
     * Tears down the SDK. Each step tolerates failure independently so that a failing step does not prevent
     * the remaining steps from running.
     */
    private fun tearDown(deleteData: Boolean) {
        started = false
        runCatching { errorHandler.close() }
        runCatching { otelSdkConfig.value.disableDataExport() }
        runCatching { instrumentationRegistry.close() }
        runCatching { resetHucInstrumentation() }
        runCatching { threadBlockageService?.close() }

        val rootDir = runCatching { appContext.filesDir }.getOrNull()
        val fallbackDir = runCatching { appContext.cacheDir }.getOrNull()
        runCatching { processStateTracker.close() }
        runCatching { networkConnectivityService.close() }
        runCatching { startupTracker.close() }
        runCatching { otelSdkConfig.value.close() }
        runCatching { sessionOrchestrator.close() }
        // queue eviction before the scheduling service shuts down the HTTP worker it shares, or the submit is rejected
        runCatching {
            workerThreadPool.backgroundWorker(Worker.Background.HttpRequestWorker).submit {
                okHttpClient.evictConnections()
            }
        }
        runCatching { schedulingService?.shutdown() }
        runCatching { cancelHttpRequests() }
        runCatching { workerThreadPool.close() }
        runCatching { eventBus.stop() }

        if (!deleteData || rootDir == null || fallbackDir == null) {
            return
        }
        val executor = runCatching { Executors.newSingleThreadExecutor() }.getOrNull() ?: return
        executor.execute {
            runCatching {
                if (!workerThreadPool.awaitTermination(WORKER_TERMINATION_TIMEOUT_MS)) {
                    Log.w("[Embrace]", "Timed out waiting for Embrace workers to finish before deleting data.")
                }
                StorageLocation.entries.map {
                    it.asFile(
                        errorHandler = null,
                        rootDirSupplier = { rootDir },
                        fallbackDirSupplier = { fallbackDir },
                    ).value
                }.forEach {
                    it.deleteRecursively()
                }
            }.onFailure { exception ->
                Log.e("[Embrace]", "An error occurred while trying to disable Embrace SDK.", exception)
            }
        }
        // lets the queued deletion finish, then releases the worker thread
        executor.shutdown()
    }

    override fun applicationInitStart() {
        if (applicationInitStartMs == null) {
            applicationInitStartMs = clock.now()
        }
    }

    override fun applicationInitEnd() {
        appStartupDataCollector.run {
            this@RealSdkInstance.applicationInitStartMs?.let { applicationInitStart(it) }
            applicationInitEnd()
        }
    }

    /**
     * Performs bootstrapping by setting required values where there is an interdependency
     * between modules.
     */
    internal fun postInit() {
        EmbTrace.trace(sectionName = "post-init", recordDuration = true) {
            payloadCachingService?.run {
                spanRepository.addSpanChangeListener { reportBackgroundActivityStateChange() }
            }

            metadataService.precomputeValues()

            // Start the log orchestrator
            logSink.registerLogStoredCallback {
                logOrchestrator.onLogsAdded()
            }
            telemetryDestination.sessionUpdateAction = sessionOrchestrator::onSessionDataUpdate
            telemetryDestination.currentStatesProvider = instrumentationRegistry::getCurrentStates

            // Start the orchestrator and create the first session part once all the dependencies have been created and wired up
            sessionOrchestrator.start()
        }
    }

    /**
     * Registers listeners for various lifecycle/system callbacks.
     */
    private fun registerListeners() {
        errorHandler.guardSdkCode(SdkErrorType.SdkInitPhaseFail) {
            EmbTrace.trace("service-registration") {
                startupTracker.register(appContext as Application)

                // periodically fail any in-flight spans that have exceeded their timeout, so leaked spans
                // are terminated and their memory released even during a long-running session.
                workerThreadPool.backgroundWorker(Worker.Background.NonIoRegWorker).scheduleWithFixedDelay(
                    {
                        errorHandler.guardSdkCode(SdkErrorType.SpanTimeoutSweepFail) {
                            spanRepository.stopTimedOutSpans(clock.now())
                        }
                    },
                    SPAN_TIMEOUT_SWEEP_INTERVAL_MS,
                    SPAN_TIMEOUT_SWEEP_INTERVAL_MS,
                    TimeUnit.MILLISECONDS,
                )

                sessionPartTracker.addSessionPartChangeListener {
                    configService.networkBehavior.domainCountLimiter.reset()
                }

                processStateTracker.addListener(appStartupDataCollector)

                threadBlockageService?.let {
                    processStateTracker.addListener(it)
                    sessionPartTracker.addSessionPartChangeListener(it)
                }

                sessionPartTracker.addSessionPartChangeListener(attachmentService)
                sessionPartTracker.addSessionPartChangeListener(logLimitingService)
                sessionPartTracker.addSessionPartChangeListener(errorHandler::resetLimit)
            }
        }
    }

    /**
     * Loads instrumentation via SPI and legacy methods.
     */
    private fun loadInstrumentation() {
        errorHandler.guardSdkCode(SdkErrorType.SdkInitPhaseFail) {
            EmbTrace.trace(sectionName = "load-instrumentation", recordDuration = true) {
                instrumentationRegistry.loadInstrumentations(loadInstrumentationProviders(), instrumentationArgs)

                threadBlockageService?.startCapture()
                lastRunCrashVerifier.readAndCleanMarkerAsync(
                    workerThreadPool.backgroundWorker(Worker.Background.IoRegWorker),
                )
            }

            initializeHucInstrumentation()
        }
    }

    /**
     * Loads the [InstrumentationProvider] implementations declared via SPI. Before making changes
     * to this function please study R8, as currently it optimizes out lookup and reflection from
     * the startup path.
     *
     * See R8 for further details on how ServiceLoaderRewriter optimizes SPI:
     * https://r8.googlesource.com/r8/+/refs/heads/main/src/main/java/com/android/tools/r8/ir/optimize/ServiceLoaderRewriter.java
     */
    private fun loadInstrumentationProviders(): List<InstrumentationProvider> {
        val providers = mutableListOf<InstrumentationProvider>()
        val iterator = ServiceLoader.load(InstrumentationProvider::class.java, InstrumentationProvider::class.java.classLoader).iterator()

        while (errorHandler.guardSdkCode(SdkErrorType.InstrumentationRegFail, false, iterator::hasNext)) {
            errorHandler.guardSdkCode(SdkErrorType.InstrumentationRegFail) { providers.add(iterator.next()) }
        }
        return providers
    }

    /**
     * Initializes HttpUrlConnection instrumentation via reflection, if the module is present and capture is enabled.
     */
    private fun initializeHucInstrumentation() {
        val networkBehavior = configService.networkBehavior
        errorHandler.guardSdkCode(SdkErrorType.InstrumentationRegFail) {
            if (networkBehavior.isHttpUrlConnectionCaptureEnabled()) {
                EmbTrace.trace(sectionName = "huc-init", recordDuration = true) {
                    val trackerClass = Class.forName("io.embrace.android.embracesdk.instrumentation.huc.HttpUrlConnectionTracker")
                    val objectField = trackerClass.getDeclaredField("INSTANCE")
                    val trackerObject = objectField.get(null)
                    val initMethod = trackerClass.getDeclaredMethod(
                        "registerUrlStreamHandlerFactory",
                        Boolean::class.java,
                        InstrumentationArgs::class.java,
                        NetworkRequestDataSource::class.java,
                        NetworkCaptureDataSource::class.java,
                    )
                    initMethod.invoke(
                        trackerObject,
                        networkBehavior.isRequestContentLengthCaptureEnabled(),
                        instrumentationArgs,
                        instrumentationRegistry.findByType(NetworkRequestDataSource::class),
                        instrumentationRegistry.findByType(NetworkCaptureDataSource::class),
                    )
                }
            }
        }
    }

    /**
     * Cancels HTTP calls that are already running, which would otherwise block worker shutdown until they time out.
     */
    private fun cancelHttpRequests() {
        if (okHttpClient.isInitialized()) {
            okHttpClient.value.dispatcher.cancelAll()
        }
    }

    private fun resetHucInstrumentation() {
        if (configService.networkBehavior.isHttpUrlConnectionCaptureEnabled()) {
            val trackerClass = Class.forName("io.embrace.android.embracesdk.instrumentation.huc.HttpUrlConnectionTracker")
            val trackerObject = trackerClass.getDeclaredField("INSTANCE").get(null)
            trackerClass.getDeclaredMethod("reset").invoke(trackerObject)
        }
    }

    /**
     * Performs post-load instrumentation tasks such as setting listeners.
     */
    internal fun postLoadInstrumentation() {
        errorHandler.guardSdkCode(SdkErrorType.SdkInitPhaseFail) {
            // setup crash teardown handlers
            val registry = instrumentationRegistry
            registry.findByType(JvmCrashDataSource::class)?.apply {
                threadBlockageService?.let(::addCrashTeardownHandler)
                addCrashTeardownHandler(logOrchestrator)
                addCrashTeardownHandler(sessionOrchestrator)
                addCrashTeardownHandler(crashMarker)
                payloadStore?.let(::addCrashTeardownHandler)
            }
            workerThreadPool.backgroundWorker(Worker.Background.NonIoRegWorker).submit {
                if (!started) {
                    return@submit
                }
                errorHandler.guardSdkCode(SdkErrorType.SdkInitPhaseFail) {
                    registry.findByType(NetworkStatusDataSource::class)?.let(networkConnectivityService::addNetworkConnectivityListener)
                }
                errorHandler.guardSdkCode(SdkErrorType.SdkInitPhaseFail) {
                    registry.findByType(NetworkStateDataSource::class)?.let(networkConnectivityService::addNetworkConnectivityListener)
                }
                errorHandler.guardSdkCode(SdkErrorType.SdkInitPhaseFail, networkConnectivityService::register)
            }
        }
    }

    /**
     * Trigger sending cached data on disk.
     */
    private fun triggerPayloadSend() {
        errorHandler.guardSdkCode(SdkErrorType.SdkInitPhaseFail) {
            val worker = workerThreadPool.backgroundWorker(Worker.Background.IoRegWorker)
            worker.submit {
                if (!started) {
                    return@submit
                }
                errorHandler.guardSdkCode(SdkErrorType.PayloadResurrectionFail) {
                    // Get real network status as soon as we can on the worker.
                    // This allows the delivery triggered by resurrection to get the most accurate network status as possible
                    // so that it won't waste time trying to send when the device is known to be offline.
                    errorHandler.guardSdkCode(SdkErrorType.SdkInitPhaseFail) {
                        schedulingService?.let(networkConnectivityService::addNetworkConnectivityListener)
                    }

                    // process multi-file session part directories before resurrection can run. The reader resurrects
                    // dead parts itself, so a part this pass doesn't reach is still resurrected by a later pass
                    errorHandler.guardSdkCode(SdkErrorType.SdkInitPhaseFail) {
                        sessionPartReader?.readPersistedSessionParts(true)
                    }

                    val resurrectionService = payloadResurrectionService
                    if (resurrectionService != null) {
                        schedulingService?.let { scheduler ->
                            resurrectionService.addResurrectionCompleteListener(scheduler::onResurrectionComplete)
                        }
                        resurrectionService.resurrectOldPayloads(
                            nativeCrashServiceProvider = {
                                instrumentationRegistry.findByType(NativeCrashDataSource::class)
                            },
                            userSessionRestoreDecisionProvider = {
                                sessionOrchestrator.userSessionRestoreDecision
                            },
                            pendingSessionPartIdsProvider = {
                                sessionPartReader?.storedSessionPartIds() ?: emptySet()
                            },
                        )
                    } else {
                        val payloadCount = cacheStorageService?.getUndeliveredPayloads()?.size ?: 0
                        errorHandler.trackSdkError(
                            type = SdkErrorType.PayloadResurrectionFail,
                            throwable = IllegalStateException(
                                "Resurrection service not found. Undelivered payloads not processed: $payloadCount",
                            ),
                        )
                    }
                }

                // always unblock the scheduler, even if resurrection didn't run or failed partway, as otherwise
                // non-crash payloads would never be delivered in this process
                schedulingService?.onResurrectionComplete()
            }
        }
    }

    /**
     * Mark SDK initialization as complete.
     */
    private fun markSdkInitComplete(sdkInitDurationsProvider: () -> Map<String, Long>) {
        errorHandler.guardSdkCode(SdkErrorType.SdkInitPhaseFail) {
            EmbTrace.trace("startup-tracking") {
                val sdkInitDurations = sdkInitDurationsProvider()
                val resourceUsageTracker = sdkInitResourceUsageTracker
                resourceUsageTracker.captureEnd()
                startupService.setSdkStartupInfo(
                    startTimeMs = sdkStartTimeMs,
                    endTimeMs = clock.now(),
                    endState = processStateTracker.getAppState(),
                    threadName = Thread.currentThread().name,
                    attributesProvider = {
                        sdkInitDurations.toSdkInitDurationAttributes() +
                            resourceUsageTracker.buildAttributes() +
                            sdkInitEnvironmentAttributes(
                                nowMs = clock.now(),
                                errorHandler = errorHandler,
                                packageInfo = instrumentationArgs.packageInfo,
                                powerManagerProvider = {
                                    instrumentationArgs.systemService(Context.POWER_SERVICE)
                                },
                                activityManagerProvider = {
                                    instrumentationArgs.systemService(Context.ACTIVITY_SERVICE)
                                },
                                prefsFileSizeProvider = { defaultPrefsFile(appContext)?.length() },
                            ) {
                                appContext.applicationInfo?.sourceDir?.let { sourceDir ->
                                    ArtOptimizationState.create(
                                        apkPath = sourceDir,
                                        primaryAbi = primaryAbi,
                                        errorHandler = errorHandler,
                                    )
                                }
                            }
                    },
                )
            }
            workerThreadPool.backgroundWorker(Worker.Background.NonIoRegWorker).submit {
                if (started) {
                    startupService.recordSdkInitSpan()
                }
            }
            val appId = configService.appId
            val startMsg = "Embrace SDK version ${BuildConfig.VERSION_NAME} started" +
                (appId?.let { " for appId = $it" } ?: " without an app ID")
            errorHandler.log(startMsg, severity = ErrorHandler.Severity.INFO)
        }
    }

    /**
     * Attempt to identify the size of the host app's default `SharedPreferences` file without actually opening it.
     * This file currently hosts the SDK's key-value store, and the bigger it is, the more impact it will have on
     * SDK init time if the SDK is the thing that opens it first.
     *
     * Returns null if the file is absent, so a missing file is not reported.
     */
    private fun defaultPrefsFile(context: Context): File? =
        File(File(context.applicationInfo.dataDir, "shared_prefs"), "${context.packageName}_preferences.xml")
            .takeIf(File::exists)
}

private const val SPAN_TIMEOUT_SWEEP_INTERVAL_MS = 30_000L

package io.embrace.android.embracesdk.testframework.actions

import android.app.ActivityManager
import android.content.Context
import android.os.Looper
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.testing.TestLifecycleOwner
import io.embrace.android.embracesdk.concurrency.BlockingScheduledExecutorService
import io.embrace.android.embracesdk.fakes.FakeClock
import io.embrace.android.embracesdk.fakes.FakeErrorHandler
import io.embrace.android.embracesdk.fakes.FakeJniDelegate
import io.embrace.android.embracesdk.fakes.FakeNetworkConnectivityService
import io.embrace.android.embracesdk.fakes.FakePayloadStorageService
import io.embrace.android.embracesdk.fakes.FakeSharedObjectLoader
import io.embrace.android.embracesdk.fakes.OtelSdkMode
import io.embrace.android.embracesdk.fakes.TestUuidSource
import io.embrace.android.embracesdk.fakes.config.FakeInstrumentedConfig
import io.embrace.android.embracesdk.fakes.fakeIncompleteSessionEnvelope
import io.embrace.android.embracesdk.fakes.fakeContext
import io.embrace.android.embracesdk.fakes.injection.TestInitServices
import io.embrace.android.embracesdk.fakes.injection.FakeWorkerThreadPool
import io.embrace.android.embracesdk.fixtures.fakeCachedSessionStoredTelemetryMetadata
import io.embrace.android.embracesdk.internal.arch.InstrumentationProvider
import io.embrace.android.embracesdk.internal.clock.millisToNanos
import io.embrace.android.embracesdk.internal.config.BuildInfo
import io.embrace.android.embracesdk.internal.config.ConfigService
import io.embrace.android.embracesdk.internal.delivery.debug.DeliveryTracer
import io.embrace.android.embracesdk.internal.delivery.storage.PayloadStorageServiceImpl
import io.embrace.android.embracesdk.internal.delivery.storage.StorageLocation
import io.embrace.android.embracesdk.internal.delivery.storage.asFile
import io.embrace.android.embracesdk.internal.injection.ConfigLoadServices
import io.embrace.android.embracesdk.internal.injection.ConfigLoadServicesSupplier
import io.embrace.android.embracesdk.internal.injection.PreStartServices
import io.embrace.android.embracesdk.internal.injection.SdkOverrides
import io.embrace.android.embracesdk.internal.injection.WorkerThreadPool
import io.embrace.android.embracesdk.internal.injection.WorkerThreadPoolImpl
import io.embrace.android.embracesdk.internal.injection.createConfigService
import io.embrace.android.embracesdk.internal.instrumentation.crash.ndk.jniDelegateTestOverride
import io.embrace.android.embracesdk.internal.instrumentation.crash.ndk.sharedObjectLoaderTestOverride
import io.embrace.android.embracesdk.internal.instrumentation.startup.SdkInitResourceUsageTracker
import io.embrace.android.embracesdk.internal.logging.ErrorHandlerImpl
import io.embrace.android.embracesdk.internal.logging.SdkErrorType
import io.embrace.android.embracesdk.internal.payload.Attribute
import io.embrace.android.embracesdk.internal.payload.Envelope
import io.embrace.android.embracesdk.internal.payload.EnvelopeMetadata
import io.embrace.android.embracesdk.internal.payload.EnvelopeResource
import io.embrace.android.embracesdk.internal.payload.NativeCrashData
import io.embrace.android.embracesdk.internal.payload.SessionPartPayload
import io.embrace.android.embracesdk.internal.payload.Span
import io.embrace.android.embracesdk.internal.prefs.createKeyValueStore
import io.embrace.android.embracesdk.internal.serialization.PlatformSerializer
import io.embrace.android.embracesdk.internal.session.lifecycle.AndroidxProcessLifecycleTracker
import io.embrace.android.embracesdk.internal.session.persistence.CompletedSpansWriter
import io.embrace.android.embracesdk.internal.session.persistence.SessionMetadataWriter
import io.embrace.android.embracesdk.internal.session.persistence.SessionPartDirectory
import io.embrace.android.embracesdk.internal.session.persistence.SessionPartWriteTarget
import io.embrace.android.embracesdk.internal.store.KeyValueStore
import io.embrace.android.embracesdk.internal.utils.UuidSource
import io.embrace.android.embracesdk.internal.utils.VersionChecker
import io.embrace.android.embracesdk.internal.vitals.vitalsVersionCheckerTestOverride
import io.embrace.android.embracesdk.internal.worker.BackgroundWorker
import io.embrace.android.embracesdk.internal.worker.Worker
import io.embrace.android.embracesdk.internal.worker.Worker.Background.NonIoRegWorker
import io.embrace.android.embracesdk.semconv.EmbCommonAttributes
import io.embrace.android.embracesdk.semconv.EmbSessionAttributes
import io.embrace.android.embracesdk.semconv.ExperimentalSemconv
import io.embrace.android.embracesdk.testframework.CapturedServices
import io.embrace.android.embracesdk.testframework.InMemoryPrefsContext
import io.embrace.android.embracesdk.testframework.SdkIntegrationTestRule
import org.robolectric.Shadows
import org.robolectric.shadows.ShadowLooper
import java.io.File
import java.util.concurrent.TimeUnit.MILLISECONDS
import java.util.concurrent.atomic.AtomicLong
import kotlin.random.Random
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.seconds

/**
 * Test harness for which an instance is generated each test run and provided to the test by the Rule
 *
 * Defaults to a [TestUuidSource] that randomizes the UUIDs it generates. A custom [UuidSource] can be
 * used instead for, say, tests that require predictable UUID sequences to be generated.
 *
 * [simulatedSdkInt] makes the startup trace, UI load and vitals instrumentation take the branches
 * they would on that API level, so a test can cover an older platform on Robolectric's default SDK
 * rather than paying for an extra sandbox.
 */
internal class EmbraceSetupInterface(
    private val workersToFake: List<Worker.Background> = emptyList(),
    private val threadBlockageWatchdogThread: Thread? = null,
    fakeStorageLayer: Boolean = false,
    ignoredSdkErrors: List<SdkErrorType> = listOf(),
    val fakeClock: FakeClock = FakeClock(currentTime = SdkIntegrationTestRule.DEFAULT_SDK_START_TIME_MS),
    private val uuidSource: UuidSource = TestUuidSource(Random.nextInt()),
    simulatedSdkInt: Int? = null,
    private val payloadStorageLimit: Int = PayloadStorageServiceImpl.DEFAULT_STORAGE_LIMIT,
) {
    private val processIdentifier: String = uuidSource.createUuid()

    private val simulatedVersionChecker: VersionChecker? = simulatedSdkInt?.let { sdkInt ->
        VersionChecker { min -> sdkInt >= min }
    }.also {
        vitalsVersionCheckerTestOverride = it
    }

    val fakeNetworkConnectivityService = FakeNetworkConnectivityService()
    val fakeJniDelegate = FakeJniDelegate().also {
        jniDelegateTestOverride = it
    }
    val fakeSharedObjectLoader = FakeSharedObjectLoader().also {
        sharedObjectLoaderTestOverride = it
    }
    val fakeLifecycleOwner: TestLifecycleOwner = TestLifecycleOwner(initialState = Lifecycle.State.INITIALIZED)
    val fakePayloadStorageService = if (fakeStorageLayer) {
        FakePayloadStorageService(processIdentifier)
    } else {
        null
    }
    val fakeCacheStorageService = if (fakeStorageLayer) {
        FakePayloadStorageService(processIdentifier)
    } else {
        null
    }

    val shadowMainLooper: ShadowLooper = Shadows.shadowOf(Looper.getMainLooper())
    val unblockMainLooperOnNavigation: Boolean = threadBlockageWatchdogThread == null

    /**
     * When set, SDK construction throws this partway through, as the app context is resolved.
     */
    var constructionFailure: Throwable? = null

    private val fakeInitServices: TestInitServices = TestInitServices(
        clock = fakeClock,
        errorHandler = FakeErrorHandler(ignoredErrors = ignoredSdkErrors, delegate = ErrorHandlerImpl(fakeClock)),
        uuidSource = uuidSource,
        // unused: the module graph builds its OTel stack from the SDK's ConfigService
        otelSdkMode = OtelSdkMode.COMPAT,
    )

    val preStartServices: PreStartServices
        get() = fakeInitServices.preStartServices

    val initServices: TestInitServices
        get() = fakeInitServices

    private val workerThreadPool: WorkerThreadPool = initWorkerThreadPool(
        fakeInitServices = fakeInitServices,
        workersToFake = workersToFake,
        threadBlockageWatchdogThread = threadBlockageWatchdogThread
    )

    /** Runs the log orchestrator's batch checks on the fake clock rather than after real delays. */
    private val logOrchestratorExecutor = BlockingScheduledExecutorService(fakeClock, blockingMode = false)

    private val fakeContext: Context = fakeContext()

    /**
     * The SDK services that tests inspect, captured as the SDK constructs them.
     */
    val services: CapturedServices = CapturedServices(
        workerThreadPool = workerThreadPool,
        networkConnectivityService = fakeNetworkConnectivityService,
        errorHandler = fakeInitServices.errorHandler,
        appContext = fakeContext,
    )

    /**
     * The SDK's store, backed by the same `SharedPreferences` the module graph will use. Owned here
     * rather than read off the module graph because tests write to it in `setupAction`, before the module
     * graph exists.
     */
    private val keyValueStore: KeyValueStore by lazy {
        createKeyValueStore(InMemoryPrefsContext(fakeContext), fakeInitServices.jsonSerializer)
    }

    init {
        setupPlatformShadows()
    }

    fun createConfigLoadServicesSupplier(instrumentedConfig: FakeInstrumentedConfig): ConfigLoadServicesSupplier {
        fakeInitServices.instrumentedConfig = instrumentedConfig
        return { context, preStartServices ->
            ConfigLoadServices(
                context = InMemoryPrefsContext(context),
                preStartServices = preStartServices,
                instrumentedConfig = fakeInitServices.instrumentedConfig,
                jsonSerializer = fakeInitServices.jsonSerializer,
                okHttpClient = fakeInitServices.okHttpClient,
                uuidSource = fakeInitServices.uuidSource,
                workerThreadPoolSupplier = { workerThreadPool },
                configServiceSupplier = { preStartServices, instrumentedConfig, serializer, okHttpClient, workerThreadPool, persistedConfig ->
                    DecoratedConfigService(
                        createConfigService(preStartServices, instrumentedConfig, serializer, okHttpClient, workerThreadPool, persistedConfig)
                    ).also { services.configService = it }
                },
            )
        }
    }

    fun createSdkOverrides(deliveryTracer: DeliveryTracer): SdkOverrides = SdkOverrides(
        logOrchestratorWorker = { BackgroundWorker(logOrchestratorExecutor) },
        versionChecker = simulatedVersionChecker?.let { versionChecker -> { versionChecker } },
        instrumentationRegistry = { real -> FakeInstrumentationRegistry(real()).also { services.instrumentationRegistry = it } },
        deliveryTracer = { deliveryTracer },
        payloadStorageService = { real -> (fakePayloadStorageService ?: real()).also { services.payloadStorageService = it } },
        cacheStorageService = fakeCacheStorageService?.let { fake -> { fake } },
        payloadStorageLimit = { payloadStorageLimit },
        lifecycleTracker = { AndroidxProcessLifecycleTracker(fakeLifecycleOwner) },
        networkConnectivityService = { fakeNetworkConnectivityService },
        appContext = {
            constructionFailure?.let { throw it }
            fakeContext
        },
        threadBlockageService = { real ->
            (if (threadBlockageWatchdogThread != null) real() else null).also { services.threadBlockageService = it }
        },
        spanRepository = { real -> real().also { services.spanRepository = it } },
        otelSdkConfig = { real -> real().also { services.otelSdkConfig = it } },
        otelSdkWrapper = { real -> real().also { services.otelSdkWrapper = it } },
        sessionIdsProvider = { real -> real().also { services.sessionIdsProvider = it } },
        sessionOrchestrator = { real -> real().also { services.sessionOrchestrator = it } },
        experimentTrackingService = { real -> real().also { services.experimentTrackingService = it } },
        intakeService = { real -> real().also { services.intakeService = it } },
        eventBus = { real -> real().also { services.eventBus = it } },
    )

    /**
     * Runs the log batch checks that are due at the fake clock's current time.
     */
    fun runDueLogBatchChecks() {
        logOrchestratorExecutor.runCurrentlyBlocked()
    }

    /**
     * Setup a fake dead session on disk
     */
    fun setupCachedDataFromNativeCrash(crashData: StoredNativeCrashData) {
        if (fakeCacheStorageService != null) {
            if (crashData.sessionMetadata != null && crashData.partEnvelope != null) {
                fakeCacheStorageService.addPayload(crashData.sessionMetadata, crashData.partEnvelope)
            }
            if (crashData.cachedCrashEnvelopeMetadata != null && crashData.cachedCrashEnvelope != null) {
                fakeCacheStorageService.addPayload(crashData.cachedCrashEnvelopeMetadata, crashData.cachedCrashEnvelope)
            }
        }
    }

    /**
     * Writes a completed session part to the multi file persistence directory, as a previous process
     * launch would have left it behind. The SDK reads it back during startup.
     */
    fun persistSessionPart(
        userSessionId: String,
        sessionPartId: String,
        timestamp: Long = fakeClock.now(),
        uuid: String = "c2610cd1-389f-422a-bfbc-25312c7a599a",
        processIdentifier: String = this.processIdentifier,
    ) {
        val directory = SessionPartDirectory(
            timestamp = timestamp,
            uuid = uuid,
            userSessionId = userSessionId,
            sessionPartId = sessionPartId,
        )
        val sessionsDir = StorageLocation.SESSION_SPLIT.asFile(
            errorHandler = fakeInitServices.errorHandler,
            rootDirSupplier = { fakeContext.filesDir },
            fallbackDirSupplier = { fakeContext.cacheDir },
        )
        check(File(sessionsDir.value, directory.dirName).mkdirs()) {
            "Could not create session part directory ${directory.dirName}"
        }

        val target = SessionPartWriteTarget(sessionsDir, fakeClock) { directory }
        SessionMetadataWriter(
            target = target,
            metadataSource = { EnvelopeMetadata() },
            resourceSource = { EnvelopeResource(appVersion = "2.5.1") },
            envelopeVersion = "0.1.0",
            envelopeType = "spans",
            sharedLibSymbolMappingSource = { null },
            errorHandler = fakeInitServices.errorHandler,
        ).write()
        CompletedSpansWriter(target, fakeInitServices.errorHandler).write(
            listOf(persistedSessionSpan(userSessionId, sessionPartId, timestamp, processIdentifier)),
        )
    }

    private fun persistedSessionSpan(
        userSessionId: String,
        sessionPartId: String,
        timestamp: Long,
        processIdentifier: String,
    ) = Span(
        traceId = "6c9b1f2ec1d34f3c9a7d0b8e5f2a4c11",
        spanId = "aaaaaaaaaaaaaaa1",
        name = "emb-session",
        startTimeNanos = timestamp.millisToNanos(),
        endTimeNanos = (timestamp + 1000L).millisToNanos(),
        status = Span.Status.UNSET,
        events = emptyList(),
        attributes = listOf(
            Attribute(key = "emb.type", data = "ux.session"),
            Attribute(key = EmbSessionAttributes.EMB_STATE, data = "foreground"),
            Attribute(key = EmbSessionAttributes.EMB_PROCESS_IDENTIFIER, data = processIdentifier),
            Attribute(key = EmbSessionAttributes.EMB_SESSION_PART_ID, data = sessionPartId),
            Attribute(key = EmbSessionAttributes.EMB_USER_SESSION_ID, data = userSessionId),
        ),
        links = emptyList(),
    )

    /**
     * Setup a fake native crash on disk
     */
    fun setupFakeNativeCrash(
        serializer: PlatformSerializer,
        crashData: StoredNativeCrashData,
    ) {
        crashData.getCrashFile().createNewFile()
        val key = crashData.getCrashFile().absolutePath
        val json = serializer.toJson(crashData.nativeCrash, NativeCrashData.serializer())
        fakeJniDelegate.addCrashRaw(key, json)
    }

    /**
     * Setup permanent session properties without using the SDK interfaces
     */
    fun setupPermanentUserSessionProperties(properties: Map<String, String>) {
        getStore().editAndCommit {
            putStringMap(
                "io.embrace.session.properties", properties
            )
        }
    }

    /**
     * Persist a fake user session before the SDK starts, simulating a store
     * from a previous process.
     */
    @OptIn(ExperimentalSemconv::class)
    fun persistUserSession(
        userSessionId: String,
        startMs: Long,
        lastActivityMs: Long,
        sessionNumber: Int = 1,
        partIndex: Int = 1,
        maxDurationSeconds: Int = 43200,
        inactivityTimeoutSeconds: Int = 1800,
        isBackgroundOnly: Boolean = false,
    ) {
        getStore().editAndCommit {
            putStringMap(
                "embrace.user_session",
                buildMap {
                    put(EmbSessionAttributes.EMB_USER_SESSION_ID, userSessionId)
                    put(EmbSessionAttributes.EMB_USER_SESSION_START_TS, startMs.toString())
                    put(EmbSessionAttributes.EMB_USER_SESSION_NUMBER, sessionNumber.toString())
                    put(EmbSessionAttributes.EMB_USER_SESSION_MAX_DURATION_SECONDS, maxDurationSeconds.toString())
                    put(EmbSessionAttributes.EMB_USER_SESSION_INACTIVITY_TIMEOUT_SECONDS, inactivityTimeoutSeconds.toString())
                    put(EmbSessionAttributes.EMB_USER_SESSION_PART_INDEX, partIndex.toString())
                    put("emb.user_session_last_activity_ts", lastActivityMs.toString())
                    if (isBackgroundOnly) {
                        put(EmbSessionAttributes.EMB_IS_BACKGROUND_ONLY_PART, "1")
                    }
                }
            )
        }
    }

    fun persistExpiredUserSession(
        sdkStartTimeMs: Long,
        userSessionId: String,
        maxDurationSeconds: Int = 43200,
        inactivityTimeoutSeconds: Int = 1800,
        cacheIncompletePartPayload: Boolean = false,
        isBackgroundOnly: Boolean = false,
        deadProcessExperiments: String? = null,
    ): Long {
        val userSessionStartTimeMs: Long = sdkStartTimeMs - maxDurationSeconds.seconds.inWholeMilliseconds - 60_000
        val lastActivityMs: Long = userSessionStartTimeMs + inactivityTimeoutSeconds.seconds.inWholeMilliseconds - 10_000
        persistUserSession(
            userSessionId = userSessionId,
            startMs = userSessionStartTimeMs,
            lastActivityMs = lastActivityMs,
            maxDurationSeconds = maxDurationSeconds,
            inactivityTimeoutSeconds = inactivityTimeoutSeconds,
            isBackgroundOnly = isBackgroundOnly,
        )
        if (cacheIncompletePartPayload) {
            val metadata = fakeCachedSessionStoredTelemetryMetadata.copy(timestamp = userSessionStartTimeMs)
            val envelope = fakeIncompleteSessionEnvelope(
                userSessionId = userSessionId,
                startMs = metadata.timestamp,
                processIdentifier = metadata.processIdentifier
            )
            checkNotNull(fakeCacheStorageService) {
                "Fake storage layer not initialized"
            }.addPayload(
                metadata = metadata,
                data = if (deadProcessExperiments == null) {
                    envelope
                } else {
                    envelope.withExperimentsOnSessionPartSpan(deadProcessExperiments)
                }
            )
        }

        return userSessionStartTimeMs
    }

    fun getClock(): FakeClock = fakeClock

    /**
     * Returns a copy of the envelope whose dead session part span carries the given serialized experiment records.
     */
    @OptIn(ExperimentalSemconv::class)
    private fun Envelope<SessionPartPayload>.withExperimentsOnSessionPartSpan(records: String): Envelope<SessionPartPayload> =
        copy(
            data = data.copy(
                spanSnapshots = data.spanSnapshots?.map { span ->
                    if (span.name == "emb-session") {
                        span.copy(
                            attributes = span.attributes?.plus(Attribute(EmbCommonAttributes.EMB_EXPERIMENTS, records)),
                        )
                    } else {
                        span
                    }
                },
            ),
        )

    fun getFakeErrorHandler(): FakeErrorHandler = fakeInitServices.errorHandler as FakeErrorHandler

    fun getFakedWorkerExecutor(worker: Worker.Background): BlockingScheduledExecutorService =
        (workerThreadPool as FakeWorkerThreadPool).executorFor(worker)

    fun getStore(): KeyValueStore = keyValueStore

    /**
     * Waits for instrumentation that opts into [InstrumentationProvider.asyncInit] to finish registering,
     * so tests can rely on its data sources existing once the SDK has started.
     */
    fun awaitAsyncInstrumentation() {
        if (NonIoRegWorker in workersToFake) {
            return
        }
        val worker = workerThreadPool.backgroundWorker(NonIoRegWorker)
        repeat(2) {
            worker.submit {}.get(ASYNC_INSTRUMENTATION_TIMEOUT_MS, MILLISECONDS)
        }
    }

    private fun setupPlatformShadows() {
        val context = fakeContext
        Shadows.shadowOf(context.packageManager).getInternalMutablePackageInfo(context.packageName).apply {
            firstInstallTime = SdkIntegrationTestRule.DEFAULT_SDK_START_TIME_MS - 5.days.inWholeMilliseconds
            lastUpdateTime = SdkIntegrationTestRule.DEFAULT_SDK_START_TIME_MS - 1.days.inWholeMilliseconds
        }
        val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        Shadows.shadowOf(activityManager).setMemoryInfo(
            ActivityManager.MemoryInfo().apply {
                availMem = 1_000_000_000L
                totalMem = 4_000_000_000L
            },
        )
    }

    /**
     * Creates an [SdkInitResourceUsageTracker] with faked providers of system data so that the attributes
     * that are recorded as a result are deterministic and can be hardcoded into the tests and golden files.
     */
    fun fakeSdkInitResourceUsageTracker(): SdkInitResourceUsageTracker {
        val elapsedMs = statProvider(INIT_WINDOW_MS)
        val cpuMs = statProvider(INIT_CPU_MS)
        val runDelayNs = statProvider(INIT_RUN_DELAY_MS.millisToNanos())
        val readBytes = statProvider(INIT_DISK_READ_KB * 1024L)
        val gcCount = statProvider(INIT_GC_COUNT)
        return SdkInitResourceUsageTracker(
            errorHandler = fakeInitServices.errorHandler,
            versionChecker = { true },
            threadCpuTimeMs = cpuMs,
            elapsedRealtimeMs = elapsedMs,
            schedstatPathProvider = { FAKE_SCHEDSTAT_PATH },
            procFileReader = { path ->
                when {
                    path.endsWith("schedstat") -> "0 ${runDelayNs()} 1\n".toByteArray()
                    path.endsWith("/io") -> "read_bytes: ${readBytes()}\n".toByteArray()
                    else -> null
                }
            },
            runtimeStatReader = { gcCount().toString() },
        )
    }

    private companion object {
        private const val ASYNC_INSTRUMENTATION_TIMEOUT_MS = 5000L
        private const val INIT_WINDOW_MS = 100L
        private const val INIT_CPU_MS = 50L
        private const val INIT_RUN_DELAY_MS = 12L
        private const val INIT_DISK_READ_KB = 48L
        private const val INIT_GC_COUNT = 2L
        private const val FAKE_SCHEDSTAT_PATH = "/proc/self/task/123/schedstat"

        /**
         * Function that returns a lambda that returns 0 when it has been invoked an odd number of times, and [evenReadValue]
         * when it's been invoked an even number of times.
         */
        private fun statProvider(evenReadValue: Long): () -> Long {
            val reads = AtomicLong()
            return {
                if (reads.incrementAndGet() % 2 == 1L) {
                    0L
                } else {
                    evenReadValue
                }
            }
        }

        fun initWorkerThreadPool(
            fakeInitServices: TestInitServices,
            workersToFake: List<Worker.Background>,
            threadBlockageWatchdogThread: Thread?,
        ): WorkerThreadPool =
            if (workersToFake.isEmpty()) {
                WorkerThreadPoolImpl(fakeInitServices.errorHandler)
            } else {
                FakeWorkerThreadPool(
                    fakeInitServices = fakeInitServices,
                    testWorkers = workersToFake,
                    threadBlockageMonitoringThread = threadBlockageWatchdogThread
                )
            }
    }

    private class DecoratedConfigService(private val impl: ConfigService) : ConfigService by impl {
        override val buildInfo: BuildInfo = BuildInfo(
            "fakeBuildId",
            "fakeBuildType",
            "fakeBuildFlavor",
            "fakeRnBundleId",
            "2.5.1",
            "99",
            "com.fake.package",
        )
    }
}

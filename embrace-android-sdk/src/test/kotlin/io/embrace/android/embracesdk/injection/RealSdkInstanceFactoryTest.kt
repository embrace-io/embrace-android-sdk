package io.embrace.android.embracesdk.injection

import android.app.Activity
import android.app.ActivityManager
import android.app.ActivityManager.RunningAppProcessInfo
import android.content.Context
import android.os.Looper
import android.os.Process
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.embrace.android.embracesdk.assertions.waitUntilConditionMet
import io.embrace.android.embracesdk.fakes.FakeAttributesMutator
import io.embrace.android.embracesdk.fakes.FakeClock
import io.embrace.android.embracesdk.fakes.FakeConfigService
import io.embrace.android.embracesdk.fakes.FakeErrorHandler
import io.embrace.android.embracesdk.fakes.FakeLogRecordExporter
import io.embrace.android.embracesdk.fakes.FakeNetworkConnectivityService
import io.embrace.android.embracesdk.fakes.FakeReadWriteLogRecord
import io.embrace.android.embracesdk.fakes.FakeSpanExporter
import io.embrace.android.embracesdk.fakes.OtelSdkMode
import io.embrace.android.embracesdk.fakes.TELEMETRY_DIRS
import io.embrace.android.embracesdk.fakes.TestPlatformSerializer
import io.embrace.android.embracesdk.fakes.injection.FakeWorkerThreadPool
import io.embrace.android.embracesdk.fakes.injection.TestInitServices
import io.embrace.android.embracesdk.fakes.testSdkStorage
import io.embrace.android.embracesdk.internal.arch.InstrumentationArgs
import io.embrace.android.embracesdk.internal.arch.InstrumentationProvider
import io.embrace.android.embracesdk.internal.arch.InstrumentationRegistry
import io.embrace.android.embracesdk.internal.arch.datasource.DataSource
import io.embrace.android.embracesdk.internal.arch.datasource.DataSourceFactory
import io.embrace.android.embracesdk.internal.arch.datasource.StateUpdate
import io.embrace.android.embracesdk.internal.arch.state.ProcessState
import io.embrace.android.embracesdk.internal.arch.state.ProcessStateListener
import io.embrace.android.embracesdk.internal.arch.ui.TapSignal
import io.embrace.android.embracesdk.internal.capture.connectivity.NetworkConnectivityService
import io.embrace.android.embracesdk.internal.clock.Clock
import io.embrace.android.embracesdk.internal.config.ConfigService
import io.embrace.android.embracesdk.internal.config.PersistedConfig
import io.embrace.android.embracesdk.internal.config.remote.OtelKotlinSdkConfig
import io.embrace.android.embracesdk.internal.config.remote.RemoteConfig
import io.embrace.android.embracesdk.internal.config.resolved.AutoDataCaptureConfig
import io.embrace.android.embracesdk.internal.config.resolved.EmbraceConfig
import io.embrace.android.embracesdk.internal.config.resolved.PersistenceConfig
import io.embrace.android.embracesdk.internal.config.resolved.StateExportMode
import io.embrace.android.embracesdk.internal.delivery.intake.IntakeService
import io.embrace.android.embracesdk.internal.injection.ConfigLoadServices
import io.embrace.android.embracesdk.internal.injection.PreStartServices
import io.embrace.android.embracesdk.internal.injection.SdkOverrides
import io.embrace.android.embracesdk.internal.injection.WorkerThreadPool
import io.embrace.android.embracesdk.internal.injection.WorkerThreadPoolImpl
import io.embrace.android.embracesdk.internal.injection.lazyOkHttpClient
import io.embrace.android.embracesdk.internal.instance.RealSdkInstance
import io.embrace.android.embracesdk.internal.instance.createRealSdkInstance
import io.embrace.android.embracesdk.internal.instrumentation.crash.CrashFileMarker
import io.embrace.android.embracesdk.internal.instrumentation.crash.jvm.JvmCrashDataSource
import io.embrace.android.embracesdk.internal.instrumentation.crash.jvm.JvmCrashInstrumentationProvider
import io.embrace.android.embracesdk.internal.instrumentation.startup.SdkInitResourceUsageTracker
import io.embrace.android.embracesdk.internal.instrumentation.thread.blockage.ThreadBlockageService
import io.embrace.android.embracesdk.internal.logging.ErrorHandler
import io.embrace.android.embracesdk.internal.logs.LogOrchestrator
import io.embrace.android.embracesdk.internal.otel.config.ExternalOtelConfig
import io.embrace.android.embracesdk.internal.otel.config.OtelSdkConfig
import io.embrace.android.embracesdk.internal.otel.sdk.OtelSdkWrapper
import io.embrace.android.embracesdk.internal.session.lifecycle.LifecycleTracker
import io.embrace.android.embracesdk.internal.session.orchestrator.PayloadStore
import io.embrace.android.embracesdk.internal.session.orchestrator.SessionOrchestrator
import io.embrace.android.embracesdk.internal.storage.SdkDir
import io.embrace.android.embracesdk.internal.utils.event.EventBus
import io.embrace.android.embracesdk.internal.worker.BackgroundWorker
import io.embrace.android.embracesdk.internal.worker.Worker
import io.embrace.android.embracesdk.semconv.EmbSessionAttributes
import io.opentelemetry.kotlin.NoopOpenTelemetry
import io.opentelemetry.kotlin.logging.model.ReadWriteLogRecord
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import java.io.File
import java.util.ServiceLoader
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

@RunWith(AndroidJUnit4::class)
internal class RealSdkInstanceFactoryTest {

    private lateinit var errorHandler: FakeErrorHandler
    private lateinit var clock: Clock
    private lateinit var context: Context

    private var otelSdkConfig: OtelSdkConfig? = null
    private var otelSdkWrapper: OtelSdkWrapper? = null
    private var instrumentationRegistry: InstrumentationRegistry? = null
    private var sessionOrchestrator: SessionOrchestrator? = null
    private var threadBlockageService: ThreadBlockageService? = null
    private var intakeService: IntakeService? = null
    private var logOrchestratorWorker: BackgroundWorker? = null
    private var lifecycleTrackerRegistered = false
    private var crashWhileLoading = false
    private var crashTornDown = false

    private val overrides = SdkOverrides(
        lifecycleTracker = { real ->
            val tracker = real()
            object : LifecycleTracker by tracker {
                override fun register(listener: ProcessStateListener) {
                    lifecycleTrackerRegistered = true
                    tracker.register(listener)
                }
            }
        },
        otelSdkConfig = { real -> real().also { otelSdkConfig = it } },
        otelSdkWrapper = { real -> real().also { otelSdkWrapper = it } },
        instrumentationRegistry = { real -> CrashWhileLoadingRegistry(real()).also { instrumentationRegistry = it } },
        logOrchestratorWorker = { real -> real().also { logOrchestratorWorker = it } },
        sessionOrchestrator = { real -> CrashRecordingOrchestrator(real()).also { sessionOrchestrator = it } },
        threadBlockageService = { real -> real().also { threadBlockageService = it } },
        intakeService = { real -> real().also { intakeService = it } },
    )

    // closed after each test, so no instance leaves worker threads, lifecycle observers or scheduled tasks behind
    private val instances = mutableListOf<RealSdkInstance>()

    @Before
    fun setup() {
        errorHandler = FakeErrorHandler(false)
        clock = FakeClock()
        context = RuntimeEnvironment.getApplication().applicationContext
    }

    @After
    fun tearDown() {
        instances.forEach(RealSdkInstance::close)
    }

    private fun createInstance(
        errorHandler: ErrorHandler = this.errorHandler,
        configService: ConfigService = FakeConfigService(),
        workerThreadPool: WorkerThreadPool? = null,
        overrides: SdkOverrides = this.overrides,
        okHttpClient: Lazy<OkHttpClient> = lazyOkHttpClient(),
        externalOtelConfig: ExternalOtelConfig.() -> Unit = {},
    ): RealSdkInstance {
        val preStartServices = PreStartServices(errorHandler, clock)
        preStartServices.externalOtelConfig.externalOtelConfig()
        return createRealSdkInstance(
            context = context,
            preStartServices = preStartServices,
            configLoadServices = ConfigLoadServices(
                context = context,
                preStartServices = preStartServices,
                okHttpClient = okHttpClient,
                workerThreadPoolSupplier = workerThreadPool?.let { pool -> { pool } },
                configServiceSupplier = { _, _, _, _, _, _ -> configService },
            ),
            sdkStartTimeMs = clock.now(),
            sdkInitResourceUsageTracker = SdkInitResourceUsageTracker(errorHandler),
            overrides = overrides,
        ).also(instances::add)
    }

    @Test
    fun `test default implementation`() {
        assertNotNull(createInstance())
        assertNotNull(otelSdkWrapper)
        assertNotNull(sessionOrchestrator)
        assertNotNull(intakeService)
    }

    @Test
    fun `metadata provider is installed before instrumentation loads`() {
        createInstance()
        val processor = checkNotNull(otelSdkConfig).logRecordProcessor

        val log = FakeReadWriteLogRecord()
        processor.onEmit(log, NoopOpenTelemetry.context.current())
        assertTrue(log.attributes.containsKey(EmbSessionAttributes.EMB_STATE))
    }

    @Test
    fun `an app is given more of state than the link on a span only if it has configured an exporter or a processor`() {
        val byDefault = FakeConfigService(
            config = EmbraceConfig(
                persistence = { PersistenceConfig(multiFileEnabled = { true }) },
                autoDataCapture = {
                    AutoDataCaptureConfig(sharedStateSpanEnabled = { true }, stateAssociationEnabled = { true })
                },
            ),
        )
        val namedAssociation = FakeConfigService(
            config = EmbraceConfig(
                persistence = { PersistenceConfig(multiFileEnabled = { true }) },
                autoDataCapture = {
                    AutoDataCaptureConfig(
                        sharedStateSpanEnabled = { true },
                        stateAssociationEnabled = { true },
                        stateExportMode = { StateExportMode.ASSOCIATION },
                    )
                },
            ),
        )
        val associated = FakeConfigService(
            config = EmbraceConfig(
                persistence = { PersistenceConfig(multiFileEnabled = { true }) },
                autoDataCapture = {
                    AutoDataCaptureConfig(
                        sharedStateSpanEnabled = { true },
                        stateAssociationEnabled = { true },
                        stateExportMode = { StateExportMode.VERBOSE },
                    )
                },
            ),
        )
        val requestedWorkers = mutableListOf<Worker>()
        val workers = WorkerThreadPoolImpl(errorHandler)
        val workerThreadPool = object : WorkerThreadPool by workers {
            override fun backgroundWorker(worker: Worker.Background): BackgroundWorker {
                requestedWorkers.add(worker)
                return workers.backgroundWorker(worker)
            }
        }

        // the mode is verbose, which is not the default, and there is nobody to emit an event for, or to keep the values of a span for
        createInstance(configService = associated, workerThreadPool = workerThreadPool)
        with(checkNotNull(otelSdkConfig)) {
            assertNotNull(spanStateLink)
            assertTrue(spanProcessor.isOnEndingRequired())
            assertTrue(trailingSpanProcessors.isEmpty())
            val resource = FakeAttributesMutator().apply(resourceAction).attributes
            assertFalse(resource.containsKey(EmbSessionAttributes.EMB_PROCESS_IDENTIFIER))
        }
        assertFalse(Worker.Background.StateExportWorker in requestedWorkers)

        // with no mode named, an app with only a span exporter gets the values on its spans, as nothing would resolve a number
        createInstance(
            configService = byDefault,
            workerThreadPool = workerThreadPool,
            externalOtelConfig = { addSpanExporter(FakeSpanExporter()) },
        )
        with(checkNotNull(otelSdkConfig)) {
            assertNotNull(spanStateLink)
            assertEquals(1, trailingSpanProcessors.size)
        }

        // with no mode named, an app with a log receiver gets association only, and so no values on its spans
        createInstance(
            configService = byDefault,
            workerThreadPool = workerThreadPool,
            externalOtelConfig = {
                addSpanExporter(FakeSpanExporter())
                addLogExporter(FakeLogRecordExporter())
            },
        )
        with(checkNotNull(otelSdkConfig)) {
            assertTrue(trailingSpanProcessors.isEmpty())
        }
        requestedWorkers.clear()

        // a mode that is named wins: association for a span exporter alone
        createInstance(
            configService = namedAssociation,
            workerThreadPool = workerThreadPool,
            externalOtelConfig = { addSpanExporter(FakeSpanExporter()) },
        )
        with(checkNotNull(otelSdkConfig)) {
            assertTrue(trailingSpanProcessors.isEmpty())
        }

        // a span exporter alone gets what the mode gives a span, and no events, as nothing receives log records
        createInstance(
            configService = associated,
            workerThreadPool = workerThreadPool,
            externalOtelConfig = { addSpanExporter(FakeSpanExporter()) },
        )
        with(checkNotNull(otelSdkConfig)) {
            assertEquals(1, trailingSpanProcessors.size)
            val resource = FakeAttributesMutator().apply(resourceAction).attributes
            assertEquals(processIdentifier, resource[EmbSessionAttributes.EMB_PROCESS_IDENTIFIER])
        }
        assertFalse(Worker.Background.StateExportWorker in requestedWorkers)

        createInstance(
            configService = associated,
            workerThreadPool = workerThreadPool,
            externalOtelConfig = { addLogExporter(FakeLogRecordExporter()) },
        )
        with(checkNotNull(otelSdkConfig)) {
            assertNotNull(spanStateLink)
            assertEquals(1, trailingSpanProcessors.size)
            val resource = FakeAttributesMutator().apply(resourceAction).attributes
            assertEquals(processIdentifier, resource[EmbSessionAttributes.EMB_PROCESS_IDENTIFIER])
        }
        assertTrue(Worker.Background.StateExportWorker in requestedWorkers)
    }

    @Test
    fun `with state associated, a log emitted before the SDK is ready carries no state, and one emitted once it is ready does`() {
        val associated = FakeConfigService(
            config = EmbraceConfig(
                persistence = { PersistenceConfig(multiFileEnabled = { true }) },
                autoDataCapture = {
                    AutoDataCaptureConfig(sharedStateSpanEnabled = { true }, stateAssociationEnabled = { true })
                },
            ),
        )
        fun emitLog(): Map<String, Any> {
            val record = FakeReadWriteLogRecord()
            // a real record gives its attributes as they are when they are read, and not as they come to be
            val log = object : ReadWriteLogRecord by record {
                override val attributes: Map<String, Any> get() = record.attributes.toMap()
            }
            checkNotNull(otelSdkConfig).logRecordProcessor.onEmit(log, NoopOpenTelemetry.context.current())
            return record.attributes.toMap()
        }
        var eventBus: EventBus? = null
        var beforeReady: Map<String, Any>? = null
        val overrides = SdkOverrides(
            otelSdkConfig = { real -> real().also { otelSdkConfig = it } },
            eventBus = { real -> real().also { eventBus = it } },
            // made once the recorder is handling updates, and before the SDK says it is ready
            sessionOrchestrator = { real ->
                real().also {
                    checkNotNull(eventBus).emit(StateUpdate.KEY, StateUpdate("network", "wifi", clock.now()))
                    beforeReady = emitLog()
                }
            },
        )

        // the app has a log exporter, so the state a log is stamped with is on the record itself
        createInstance(
            configService = associated,
            overrides = overrides,
            externalOtelConfig = { addLogExporter(FakeLogRecordExporter()) },
        )

        assertEquals(emptyList<String>(), checkNotNull(beforeReady).keys.filter { it.startsWith("emb.states.") })
        val afterReady = emitLog()
        assertEquals("wifi", afterReady["emb.states.value.network"])
        assertNotNull(afterReady["emb.states.seq"])
    }

    /**
     * The persisted config decides which OTel SDK implementation is built, so it has to be read
     * before that construction happens. Note that [RealSdkInstance.setupServices] is deliberately not called.
     */
    @Test
    fun `kotlin otel sdk selected from persisted config during init`() {
        createInstanceWithPersistedConfig(
            RemoteConfig(otelKotlinSdkConfig = OtelKotlinSdkConfig(pctEnabled = 100f)),
        )
        assertTrue(checkNotNull(otelSdkWrapper).useKotlinSdk)
    }

    @Test
    fun `kotlin otel sdk not selected when disabled by persisted config`() {
        createInstanceWithPersistedConfig(
            RemoteConfig(otelKotlinSdkConfig = OtelKotlinSdkConfig(pctEnabled = 0f)),
        )
        assertFalse(checkNotNull(otelSdkWrapper).useKotlinSdk)
    }

    private fun createInstanceWithPersistedConfig(cfg: RemoteConfig): RealSdkInstance {
        val storageDir = testSdkStorage(context).dir(SdkDir.REMOTE_CONFIG).toFile()
        File(storageDir, PersistedConfig.CONFIG_RESPONSE_FILE_NAME).outputStream().buffered().use { stream ->
            TestPlatformSerializer().toJson(cfg, RemoteConfig.serializer(), stream)
        }
        val initServices = TestInitServices(
            clock = clock,
            errorHandler = errorHandler,
            otelSdkMode = OtelSdkMode.COMPAT,
        )
        return createRealSdkInstance(
            context = context,
            preStartServices = initServices.preStartServices,
            configLoadServices = ConfigLoadServices(
                context = context,
                preStartServices = initServices.preStartServices,
                instrumentedConfig = initServices.instrumentedConfig,
                jsonSerializer = initServices.jsonSerializer,
                uuidSource = initServices.uuidSource,
                configServiceSupplier = { _, _, _, _, _, persistedConfig ->
                    FakeConfigService(otelBehavior = persistedConfig.otelBehavior)
                },
            ),
            sdkStartTimeMs = clock.now(),
            sdkInitResourceUsageTracker = SdkInitResourceUsageTracker(errorHandler),
            overrides = overrides,
        ).also(instances::add)
    }

    /**
     * The instance serves public API calls, and the calls buffered before start, once it is enabled. Instrumentation loads
     * only as its init finishes, so calls made while it loads aren't dropped.
     */
    @Test
    fun `instrumentation loads in onEnabled rather than in enable`() {
        val instance = createInstance()
        instance.enable(context)
        assertNull(checkNotNull(instrumentationRegistry).findByType(JvmCrashDataSource::class))
        instance.onEnabled()
        assertNotNull(checkNotNull(instrumentationRegistry).findByType(JvmCrashDataSource::class))
    }

    /**
     * The integration tests replace the platform's lifecycle tracking, so this checks that a real tracker reaches an enabled
     * instance. It uses the tracker driven by activity callbacks, as androidx's process lifecycle is not initialized here.
     */
    @Test
    fun `real lifecycle tracking reports a foreground to the enabled instance`() {
        val configService = FakeConfigService(
            config = EmbraceConfig(
                autoDataCapture = { AutoDataCaptureConfig(activityProcessLifecycleTrackerEnabled = { true }) },
            ),
        )
        val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        shadowOf(activityManager).setProcesses(
            listOf(
                RunningAppProcessInfo().apply {
                    pid = Process.myPid()
                    importance = RunningAppProcessInfo.IMPORTANCE_CACHED
                },
            ),
        )
        createInstance(configService = configService).runInit()
        assertEquals(ProcessState.BACKGROUND.description, emittedLogState())

        Robolectric.buildActivity(Activity::class.java).setup()
        shadowOf(Looper.getMainLooper()).idle()
        assertEquals(ProcessState.FOREGROUND.description, emittedLogState())
    }

    /**
     * Only a fixed number of SDK errors are sent per session part, so the limit must reset when a new part starts.
     */
    @Test
    fun `a new session part resets the SDK error limit`() {
        var limitResets = 0
        val countingErrorHandler = object : ErrorHandler by errorHandler {
            override fun resetLimit() {
                limitResets++
            }
        }
        createInstance(countingErrorHandler).runInit()
        val resetsAtStart = limitResets

        checkNotNull(sessionOrchestrator).onForeground()
        assertEquals(resetsAtStart + 1, limitResets)
    }

    /**
     * Once the user opts out, the data must be gone even if the process ends while disable waits for busy workers to finish.
     * The instance isn't started, so nothing else touches storage meanwhile.
     */
    @Test
    fun `disable deletes data without waiting for busy workers`() {
        val instance = createInstance()
        val sentinels = storageDirs().map {
            File(it, "sentinel").apply { writeText("data") }
        }
        val releaseWorker = CountDownLatch(1)
        checkNotNull(logOrchestratorWorker).submit {
            releaseWorker.await(10, TimeUnit.SECONDS)
        }

        try {
            instance.disable()

            // well within the wait for the blocked worker
            waitUntilConditionMet(
                waitTimeMs = 1000,
                dataProvider = { sentinels.none(File::exists) },
                condition = { it },
            )
        } finally {
            releaseWorker.countDown()
        }
    }

    @Test
    fun `crash teardown handlers are registered with the crash data source`() {
        val instance = createInstance()
        val dataSource = CrashHandlerDataSource()

        instance.registerCrashTeardownHandlers(dataSource)

        val handlers = dataSource.handlers
        assertEquals(5, handlers.size)
        assertSame(checkNotNull(threadBlockageService), handlers[0])
        assertTrue(handlers[1] is LogOrchestrator)
        assertSame(sessionOrchestrator, handlers[2])
        assertTrue(handlers[3] is CrashFileMarker)
        assertTrue(handlers[4] is PayloadStore)
    }

    @Test
    fun `lifecycle observer is registered when the instance is enabled`() {
        val instance = createInstance()
        assertFalse(lifecycleTrackerRegistered)
        instance.enable(context)
        assertTrue(lifecycleTrackerRegistered)
    }

    @Test
    fun `immediate disable runs queued cleanup and skips queued start work`() {
        val connectivityService = FakeNetworkConnectivityService()
        val workerThreadPool = FakeWorkerThreadPool(
            fakeInitServices = TestInitServices(clock = clock, errorHandler = errorHandler, otelSdkMode = OtelSdkMode.COMPAT),
            testWorkers = listOf(Worker.Background.NonIoRegWorker, Worker.Background.IoRegWorker),
        )
        val server = MockWebServer().apply { enqueue(MockResponse()) }
        val okHttpClient = lazyOkHttpClient()
        okHttpClient.value.newCall(Request.Builder().url(server.url("/")).build()).execute().close()
        assertEquals(1, okHttpClient.value.connectionPool.idleConnectionCount())

        val storageDirs = TELEMETRY_DIRS.map { testSdkStorage(context).dir(it).toFile() }
        File(storageDirs.first().apply(File::mkdirs), "payload").writeText("{}")

        val instance = createInstance(
            workerThreadPool = workerThreadPool,
            overrides = SdkOverrides(
                lifecycleTracker = overrides.lifecycleTracker,
                networkConnectivityService = { connectivityService },
            ),
            okHttpClient = okHttpClient,
        )
        instance.enable(context)
        instance.onEnabled()
        val nonIoWorker = workerThreadPool.executorFor(Worker.Background.NonIoRegWorker)
        val ioWorker = workerThreadPool.executorFor(Worker.Background.IoRegWorker)
        instance.disable()

        // eviction is queued on the HTTP worker during teardown and must run before the workers terminate
        assertTrue(workerThreadPool.awaitTermination(AWAIT_TIMEOUT_MS))
        assertEquals(0, okHttpClient.value.connectionPool.connectionCount())

        // data deletion waits for worker termination on its own thread
        val deadline = System.currentTimeMillis() + AWAIT_TIMEOUT_MS
        while (storageDirs.any(File::exists) && System.currentTimeMillis() < deadline) {
            Thread.sleep(10)
        }
        assertFalse(storageDirs.any(File::exists))

        // start work queued on the faked workers before teardown still runs, but must not do anything
        nonIoWorker.runCurrentlyBlocked()
        ioWorker.runCurrentlyBlocked()
        assertFalse(connectivityService.registered)
        assertTrue(connectivityService.listeners.isEmpty())
        server.shutdown()
    }

    @Test
    fun `event sources stop before data sources are disabled`() {
        var eventBus: EventBus? = null
        var taps = 0
        var tapsWhenDisabling: Int? = null
        val closed = mutableListOf<String>()
        var closedWhenDisabling: List<String>? = null
        val instance = createInstance(
            overrides = SdkOverrides(
                lifecycleTracker = { real ->
                    val tracker = real()
                    object : LifecycleTracker by tracker {
                        override fun close() {
                            closed += "process state"
                            tracker.close()
                        }
                    }
                },
                networkConnectivityService = { real ->
                    val service = real()
                    object : NetworkConnectivityService by service {
                        override fun close() {
                            closed += "connectivity"
                            service.close()
                        }
                    }
                },
                eventBus = { real -> real().also { eventBus = it } },
                instrumentationRegistry = { real ->
                    val registry = real()
                    object : InstrumentationRegistry by registry {
                        override fun close() {
                            checkNotNull(eventBus).emit(TapSignal.KEY, TapSignal("tap", 0f, 0f))
                            tapsWhenDisabling = taps
                            closedWhenDisabling = closed.toList()
                            registry.close()
                        }
                    }
                },
            ),
        )
        instance.runInit()
        val bus = checkNotNull(eventBus)
        bus.addHandler(TapSignal.KEY) { taps++ }
        bus.emit(TapSignal.KEY, TapSignal("tap", 0f, 0f))
        assertEquals(1, taps)

        instance.disable()

        // the event bus delivers nothing more, and the process state and connectivity listeners are released
        assertEquals(1, tapsWhenDisabling)
        assertEquals(listOf("process state", "connectivity"), closedWhenDisabling)
    }

    /**
     * The JVM crash handler is installed as soon as its data source loads, so a crash while the rest of the instrumentation
     * loads must still tear down the SDK.
     */
    @Test
    fun `a crash while instrumentation loads is torn down`() {
        crashWhileLoading = true
        createInstance().runInit()

        assertFalse(crashWhileLoading)
        assertTrue(crashTornDown)
    }

    /**
     * The SDK registers the JVM crash instrumentation itself, so it must not also be discovered via SPI, which would register
     * it a second time.
     */
    @Test
    fun `the JVM crash provider is not discovered via SPI`() {
        val discovered = ServiceLoader.load(InstrumentationProvider::class.java, InstrumentationProvider::class.java.classLoader)
        assertFalse(discovered.any { it is JvmCrashInstrumentationProvider })
    }

    // runs the instance's init as SdkStateHolder does, without a pre-start buffer to transition from
    private fun RealSdkInstance.runInit() {
        enable(context)
        onEnabled()
    }

    private fun storageDirs(): List<File> = TELEMETRY_DIRS.map {
        testSdkStorage(context).dir(it).toFile()
    }

    private fun emittedLogState(): Any? {
        val log = FakeReadWriteLogRecord()
        checkNotNull(otelSdkConfig).logRecordProcessor.onEmit(log, NoopOpenTelemetry.context.current())
        return log.attributes[EmbSessionAttributes.EMB_STATE]
    }

    /**
     * When [crashWhileLoading] is set, reports a JVM crash as the first instrumentation after the crash data source loads.
     */
    private inner class CrashWhileLoadingRegistry(
        private val impl: InstrumentationRegistry,
    ) : InstrumentationRegistry by impl {
        override fun loadInstrumentations(
            instrumentationProviders: Iterable<InstrumentationProvider>,
            args: InstrumentationArgs,
        ) {
            impl.loadInstrumentations(instrumentationProviders.map { CrashingProvider(it) }, args)
        }

        private inner class CrashingProvider(private val provider: InstrumentationProvider) : InstrumentationProvider by provider {
            override fun register(args: InstrumentationArgs): DataSourceFactory<DataSource>? {
                val crashSource = impl.findByType(JvmCrashDataSource::class)
                if (crashWhileLoading && crashSource != null) {
                    crashWhileLoading = false
                    crashSource.logUnhandledJvmThrowable(IllegalStateException("crash while loading"))
                }
                return provider.register(args)
            }
        }
    }

    private inner class CrashRecordingOrchestrator(
        private val impl: SessionOrchestrator,
    ) : SessionOrchestrator by impl {
        override fun handleCrash(crashId: String) {
            crashTornDown = true
            impl.handleCrash(crashId)
        }
    }

    private companion object {
        const val AWAIT_TIMEOUT_MS = 5000L
    }
}

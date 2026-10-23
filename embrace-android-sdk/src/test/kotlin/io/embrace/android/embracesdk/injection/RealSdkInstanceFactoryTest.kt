package io.embrace.android.embracesdk.injection

import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.embrace.android.embracesdk.fakes.FakeClock
import io.embrace.android.embracesdk.fakes.FakeConfigService
import io.embrace.android.embracesdk.fakes.FakeErrorHandler
import io.embrace.android.embracesdk.fakes.FakeNetworkConnectivityService
import io.embrace.android.embracesdk.fakes.FakeReadWriteLogRecord
import io.embrace.android.embracesdk.fakes.OtelSdkMode
import io.embrace.android.embracesdk.fakes.TestPlatformSerializer
import io.embrace.android.embracesdk.fakes.injection.FakeWorkerThreadPool
import io.embrace.android.embracesdk.fakes.injection.TestInitServices
import io.embrace.android.embracesdk.internal.arch.InstrumentationRegistry
import io.embrace.android.embracesdk.internal.arch.state.ProcessState
import io.embrace.android.embracesdk.internal.arch.state.ProcessStateListener
import io.embrace.android.embracesdk.internal.clock.Clock
import io.embrace.android.embracesdk.internal.config.PersistedConfig
import io.embrace.android.embracesdk.internal.config.remote.OtelKotlinSdkConfig
import io.embrace.android.embracesdk.internal.config.remote.RemoteConfig
import io.embrace.android.embracesdk.internal.delivery.intake.IntakeService
import io.embrace.android.embracesdk.internal.delivery.storage.StorageLocation
import io.embrace.android.embracesdk.internal.injection.ConfigLoadServices
import io.embrace.android.embracesdk.internal.injection.PreStartServices
import io.embrace.android.embracesdk.internal.injection.SdkOverrides
import io.embrace.android.embracesdk.internal.injection.WorkerThreadPool
import io.embrace.android.embracesdk.internal.injection.lazyOkHttpClient
import io.embrace.android.embracesdk.internal.instance.RealSdkInstance
import io.embrace.android.embracesdk.internal.instance.createRealSdkInstance
import io.embrace.android.embracesdk.internal.instrumentation.crash.CrashFileMarker
import io.embrace.android.embracesdk.internal.instrumentation.startup.SdkInitResourceUsageTracker
import io.embrace.android.embracesdk.internal.instrumentation.thread.blockage.ThreadBlockageService
import io.embrace.android.embracesdk.internal.logs.LogOrchestrator
import io.embrace.android.embracesdk.internal.otel.config.OtelSdkConfig
import io.embrace.android.embracesdk.internal.otel.sdk.OtelSdkWrapper
import io.embrace.android.embracesdk.internal.session.lifecycle.LifecycleTracker
import io.embrace.android.embracesdk.internal.session.orchestrator.PayloadStore
import io.embrace.android.embracesdk.internal.session.orchestrator.SessionOrchestrator
import io.embrace.android.embracesdk.internal.worker.Worker
import io.embrace.android.embracesdk.semconv.EmbSessionAttributes
import io.opentelemetry.kotlin.NoopOpenTelemetry
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RuntimeEnvironment
import java.io.File

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
    private var lifecycleTrackerRegistered = false

    private val overrides = SdkOverrides(
        otelSdkConfig = { real -> real().also { otelSdkConfig = it } },
        otelSdkWrapper = { real -> real().also { otelSdkWrapper = it } },
        instrumentationRegistry = { real -> real().also { instrumentationRegistry = it } },
        sessionOrchestrator = { real -> real().also { sessionOrchestrator = it } },
        threadBlockageService = { real -> real().also { threadBlockageService = it } },
        intakeService = { real -> real().also { intakeService = it } },
        lifecycleTracker = {
            object : LifecycleTracker {
                override fun getProcessState(): ProcessState = ProcessState.BACKGROUND
                override fun register(listener: ProcessStateListener) {
                    lifecycleTrackerRegistered = true
                }
                override fun close() {}
            }
        },
    )

    @Before
    fun setup() {
        errorHandler = FakeErrorHandler(false)
        clock = FakeClock()
        context = RuntimeEnvironment.getApplication().applicationContext
    }

    private fun createInstance(
        workerThreadPool: WorkerThreadPool? = null,
        overrides: SdkOverrides = this.overrides,
        okHttpClient: Lazy<OkHttpClient> = lazyOkHttpClient(),
    ): RealSdkInstance {
        val preStartServices = PreStartServices(errorHandler, clock)
        return createRealSdkInstance(
            context = context,
            preStartServices = preStartServices,
            configLoadServices = ConfigLoadServices(
                context = context,
                preStartServices = preStartServices,
                okHttpClient = okHttpClient,
                workerThreadPoolSupplier = workerThreadPool?.let { pool -> { pool } },
                configServiceSupplier = { _, _, _, _, _, _ -> FakeConfigService() },
            ),
            sdkStartTimeMs = clock.now(),
            sdkInitResourceUsageTracker = SdkInitResourceUsageTracker(errorHandler),
            overrides = overrides,
        )
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
        processor.onEmit(log, NoopOpenTelemetry.context.implicit())
        assertTrue(log.attributes.containsKey(EmbSessionAttributes.EMB_STATE))
    }

    /**
     * The persisted config decides which OTel SDK implementation is built, so it has to be read
     * before that construction happens. Note that [RealSdkInstance.postInit] is deliberately not called.
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
        val storageDir = File(context.filesDir, PersistedConfig.STORAGE_DIR_NAME).apply { mkdirs() }
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
        )
    }

    @Test
    fun `post load instrumentation hooks up listeners`() {
        val instance = createInstance()
        val dataSource = CrashHandlerDataSource()
        checkNotNull(instrumentationRegistry).add { dataSource }

        instance.postLoadInstrumentation()

        val handlers = dataSource.handlers
        assertEquals(5, handlers.size)
        assertSame(checkNotNull(threadBlockageService), handlers[0])
        assertTrue(handlers[1] is LogOrchestrator)
        assertSame(sessionOrchestrator, handlers[2])
        assertTrue(handlers[3] is CrashFileMarker)
        assertTrue(handlers[4] is PayloadStore)
    }

    @Test
    fun `lifecycle observer is registered when the instance starts`() {
        val instance = createInstance()
        assertFalse(lifecycleTrackerRegistered)
        instance.start(context)
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

        val storageDirs = StorageLocation.entries.map { File(context.filesDir, it.dir) }
        File(storageDirs.first().apply(File::mkdirs), "payload").writeText("{}")

        val instance = createInstance(
            workerThreadPool = workerThreadPool,
            overrides = SdkOverrides(
                lifecycleTracker = overrides.lifecycleTracker,
                networkConnectivityService = { connectivityService },
            ),
            okHttpClient = okHttpClient,
        )
        instance.start(context)
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

    private companion object {
        const val AWAIT_TIMEOUT_MS = 5000L
    }
}

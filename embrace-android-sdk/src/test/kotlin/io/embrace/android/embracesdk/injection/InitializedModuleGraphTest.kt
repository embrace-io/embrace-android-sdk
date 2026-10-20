package io.embrace.android.embracesdk.injection

import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.embrace.android.embracesdk.fakes.FakeClock
import io.embrace.android.embracesdk.fakes.FakeConfigService
import io.embrace.android.embracesdk.fakes.FakeInstrumentationModule
import io.embrace.android.embracesdk.fakes.FakeInternalLogger
import io.embrace.android.embracesdk.fakes.FakeReadWriteLogRecord
import io.embrace.android.embracesdk.fakes.OtelSdkMode
import io.embrace.android.embracesdk.fakes.TestPlatformSerializer
import io.embrace.android.embracesdk.fakes.injection.FakeCoreModule
import io.embrace.android.embracesdk.fakes.injection.FakeInitModule
import io.embrace.android.embracesdk.internal.arch.InstrumentationRegistry
import io.embrace.android.embracesdk.internal.clock.Clock
import io.embrace.android.embracesdk.internal.config.PersistedConfig
import io.embrace.android.embracesdk.internal.config.remote.OtelKotlinSdkConfig
import io.embrace.android.embracesdk.internal.config.remote.RemoteConfig
import io.embrace.android.embracesdk.internal.injection.ConfigLoadServices
import io.embrace.android.embracesdk.internal.injection.EssentialServiceModuleImpl
import io.embrace.android.embracesdk.internal.injection.InitializedModuleGraph
import io.embrace.android.embracesdk.internal.injection.PreStartServices
import io.embrace.android.embracesdk.internal.instance.RealSdkInstance
import io.embrace.android.embracesdk.internal.instance.createRealSdkInstance
import io.embrace.android.embracesdk.internal.instrumentation.startup.SdkInitResourceUsageTracker
import io.embrace.android.embracesdk.semconv.EmbSessionAttributes
import io.opentelemetry.kotlin.NoopOpenTelemetry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RuntimeEnvironment
import java.io.File

@RunWith(AndroidJUnit4::class)
internal class InitializedModuleGraphTest {

    private lateinit var logger: FakeInternalLogger
    private lateinit var clock: Clock
    private lateinit var coreModule: FakeCoreModule
    private lateinit var context: Context
    private lateinit var registry: InstrumentationRegistry

    @Before
    fun setup() {
        logger = FakeInternalLogger(false)
        clock = FakeClock()
        coreModule = FakeCoreModule()
        context = RuntimeEnvironment.getApplication().applicationContext
    }

    private fun createModuleGraph(): InitializedModuleGraph {
        val application = RuntimeEnvironment.getApplication()
        val preStartServices = PreStartServices(logger, clock)
        return InitializedModuleGraph(
            context = context,
            preStartServices = preStartServices,
            configLoadServices = ConfigLoadServices(
                context = context,
                preStartServices = preStartServices,
                configServiceSupplier = { _, _, _, _, _, _ -> FakeConfigService() },
            ),
            sdkStartTimeMs = clock.now(),
            sdkInitResourceUsageTracker = SdkInitResourceUsageTracker(logger),
            coreModuleSupplier = { _, _, _ -> coreModule },
            instrumentationModuleSupplier = { _, _, _, _, _, _, _, _, _ ->
                FakeInstrumentationModule(application, logger = logger).apply {
                    registry = instrumentationRegistry
                }
            },
        ).apply { start() }
    }

    @Test
    fun `test default implementation`() {
        with(createModuleGraph()) {
            assertNotNull(initModule)
            assertNotNull(openTelemetryModule)
            assertNotNull(workerThreadPool)
            assertTrue(essentialServiceModule is EssentialServiceModuleImpl)
            assertNotNull(dataCaptureServiceModule)
            assertNotNull(deliveryModule)
            assertNotNull(payloadSourceModule)
            assertEquals(clock, initModule.clock)
        }
    }

    @Test
    fun `metadata provider is installed before instrumentation loads`() {
        val moduleGraph = createModuleGraph()
        val processor = moduleGraph.openTelemetryModule.otelSdkConfig.logRecordProcessor

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
        val moduleGraph = createModuleGraphWithPersistedConfig(
            RemoteConfig(otelKotlinSdkConfig = OtelKotlinSdkConfig(pctEnabled = 100f)),
        )
        assertTrue(moduleGraph.openTelemetryModule.otelSdkWrapper.useKotlinSdk)
    }

    @Test
    fun `kotlin otel sdk not selected when disabled by persisted config`() {
        val moduleGraph = createModuleGraphWithPersistedConfig(
            RemoteConfig(otelKotlinSdkConfig = OtelKotlinSdkConfig(pctEnabled = 0f)),
        )
        assertFalse(moduleGraph.openTelemetryModule.otelSdkWrapper.useKotlinSdk)
    }

    private fun createModuleGraphWithPersistedConfig(cfg: RemoteConfig): InitializedModuleGraph {
        val storageDir = File(context.filesDir, PersistedConfig.STORAGE_DIR_NAME).apply { mkdirs() }
        File(storageDir, "most_recent_response").outputStream().buffered().use { stream ->
            TestPlatformSerializer().toJson(cfg, RemoteConfig.serializer(), stream)
        }
        val initModule = FakeInitModule(clock = clock, logger = logger, otelSdkMode = OtelSdkMode.COMPAT)
        return InitializedModuleGraph(
            context = context,
            preStartServices = initModule.preStartServices,
            configLoadServices = ConfigLoadServices(
                context = context,
                preStartServices = initModule.preStartServices,
                instrumentedConfig = initModule.instrumentedConfig,
                jsonSerializer = initModule.jsonSerializer,
                uuidSource = initModule.uuidSource,
                configServiceSupplier = { _, _, _, _, _, persistedConfig ->
                    FakeConfigService(otelBehavior = persistedConfig.otelBehavior)
                },
            ),
            sdkStartTimeMs = clock.now(),
            sdkInitResourceUsageTracker = SdkInitResourceUsageTracker(logger),
            initModuleSupplier = { initModule },
            instrumentationModuleSupplier = { _, _, _, _, _, _, _, _, _ ->
                FakeInstrumentationModule(RuntimeEnvironment.getApplication(), logger = logger)
            },
        )
    }

    @Test
    fun `post load instrumentation hooks up listeners`() {
        val moduleGraph = createModuleGraph()
        val registry = moduleGraph.instrumentationModule.instrumentationRegistry
        val dataSource = CrashHandlerDataSource()
        registry.add { dataSource }

        createRealSdkInstance(moduleGraph, clock).postLoadInstrumentation()

        val handlers = dataSource.handlers
        val expected = listOf(
            moduleGraph.threadBlockageService,
            moduleGraph.logOrchestrator,
            moduleGraph.userSessionOrchestrationModule.sessionOrchestrator,
            moduleGraph.crashMarker,
            moduleGraph.deliveryModule?.payloadStore,
        )
        assertEquals(expected, handlers)
    }
}

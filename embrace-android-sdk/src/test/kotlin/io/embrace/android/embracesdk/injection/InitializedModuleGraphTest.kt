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
import io.embrace.android.embracesdk.internal.config.resolved.EmbraceConfig
import io.embrace.android.embracesdk.internal.config.resolved.SdkModeConfig
import io.embrace.android.embracesdk.internal.injection.BootstrapServices
import io.embrace.android.embracesdk.internal.injection.EssentialServiceModuleImpl
import io.embrace.android.embracesdk.internal.injection.InitializedModuleGraph
import io.embrace.android.embracesdk.internal.injection.OpenTelemetryModuleImpl
import io.embrace.android.embracesdk.internal.injection.SdkDisabledException
import io.embrace.android.embracesdk.internal.instance.BufferingSdkInstance
import io.embrace.android.embracesdk.internal.instance.RealSdkInstance
import io.embrace.android.embracesdk.semconv.EmbSessionAttributes
import io.opentelemetry.kotlin.NoopOpenTelemetry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertThrows
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
        return InitializedModuleGraph(
            context = context,
            bootstrapServices = BootstrapServices(logger, clock),
            buffer = BufferingSdkInstance({ 0 }, logger),
            configServiceSupplier = { _, _, _, _, _ -> FakeConfigService() },
            coreModuleSupplier = { _, _, _ -> coreModule },
            instrumentationModuleSupplier = { _, _, _, _, _, _, _, _, _ ->
                FakeInstrumentationModule(application, logger = logger).apply {
                    registry = instrumentationRegistry
                }
            },
        )
    }

    @Test
    fun `test default implementation`() {
        with(createModuleGraph()) {
            assertNotNull(initModule)
            assertNotNull(openTelemetryModule)
            assertNotNull(workerThreadModule)
            assertTrue(essentialServiceModule is EssentialServiceModuleImpl)
            assertNotNull(dataCaptureServiceModule)
            assertNotNull(deliveryModule)
            assertNotNull(payloadSourceModule)
            assertEquals(clock, initModule.clock)
        }
    }

    @Test
    fun `postInit installs metadata provider before instrumentation loads`() {
        val moduleGraph = createModuleGraph()
        val processor = moduleGraph.openTelemetryModule.otelSdkConfig.logRecordProcessor

        val preLog = FakeReadWriteLogRecord()
        processor.onEmit(preLog, NoopOpenTelemetry.context.implicit())
        assertFalse(preLog.attributes.containsKey(EmbSessionAttributes.EMB_STATE))

        RealSdkInstance(moduleGraph, clock).postInit()

        val postLog = FakeReadWriteLogRecord()
        processor.onEmit(postLog, NoopOpenTelemetry.context.implicit())
        assertTrue(postLog.attributes.containsKey(EmbSessionAttributes.EMB_STATE))
    }

    /**
     * The persisted config decides which OTel SDK implementation is built, so it has to be read
     * before that construction happens. Note that [RealSdkInstance.postInit] is deliberately not called:
     * if the flag were still applied there, this would fail.
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

    /**
     * Guards the invariant that nothing constructs the OTel SDK before the config service exists.
     * Fails if OTel-touching work is reintroduced earlier in the module graph.
     */
    @Test
    fun `otel sdk is not built until the config service has been created`() {
        var initializedWhenConfigServiceBuilt: Boolean? = null
        val initModule = FakeInitModule(clock = clock, logger = logger, otelSdkMode = OtelSdkMode.COMPAT)
        val moduleGraph = InitializedModuleGraph(
            context = context,
            bootstrapServices = initModule.bootstrapServices,
            buffer = BufferingSdkInstance({ 0 }, logger),
            initModuleSupplier = { initModule },
            configServiceSupplier = { _, _, openTelemetryModule, _, _ ->
                initializedWhenConfigServiceBuilt = openTelemetryModule.currentSessionPartSpan.initialized()
                FakeConfigService()
            },
            instrumentationModuleSupplier = { _, _, _, _, _, _, _, _, _ ->
                FakeInstrumentationModule(RuntimeEnvironment.getApplication(), logger = logger)
            },
        )
        assertEquals(false, initializedWhenConfigServiceBuilt)
        assertTrue(moduleGraph.openTelemetryModule.currentSessionPartSpan.initialized())
    }

    @Test
    fun `otel sdk is not built when the sdk is remotely disabled`() {
        val initModule = FakeInitModule(clock = clock, logger = logger, otelSdkMode = OtelSdkMode.COMPAT)
        val openTelemetryModule = OpenTelemetryModuleImpl(initModule)
        assertThrows(SdkDisabledException::class.java) {
            InitializedModuleGraph(
                context = context,
                bootstrapServices = initModule.bootstrapServices,
                buffer = BufferingSdkInstance({ 0 }, logger),
                initModuleSupplier = { initModule },
                openTelemetryModuleSupplier = { _, _ -> openTelemetryModule },
                configServiceSupplier = { _, _, _, _, _ ->
                    FakeConfigService(
                        config = EmbraceConfig(sdkMode = { SdkModeConfig(sdkDisabled = { true }) }),
                    )
                },
            )
        }
        assertFalse(openTelemetryModule.currentSessionPartSpan.initialized())
    }

    private fun createModuleGraphWithPersistedConfig(cfg: RemoteConfig): InitializedModuleGraph {
        val storageDir = File(context.filesDir, PersistedConfig.STORAGE_DIR_NAME).apply { mkdirs() }
        File(storageDir, "most_recent_response").outputStream().buffered().use { stream ->
            TestPlatformSerializer().toJson(cfg, RemoteConfig.serializer(), stream)
        }
        val initModule = FakeInitModule(clock = clock, logger = logger, otelSdkMode = OtelSdkMode.COMPAT)
        return InitializedModuleGraph(
            context = context,
            bootstrapServices = initModule.bootstrapServices,
            buffer = BufferingSdkInstance({ 0 }, logger),
            initModuleSupplier = { initModule },
            configServiceSupplier = { _, _, _, _, _ -> FakeConfigService() },
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

        RealSdkInstance(moduleGraph, clock).postLoadInstrumentation()

        val handlers = dataSource.handlers
        val expected = listOf(
            moduleGraph.threadBlockageService,
            moduleGraph.logModule.logOrchestrator,
            moduleGraph.userSessionOrchestrationModule.sessionOrchestrator,
            moduleGraph.featureModule.crashMarker,
            moduleGraph.deliveryModule?.payloadStore,
        )
        assertEquals(expected, handlers)
    }
}

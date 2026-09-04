package io.embrace.android.embracesdk.injection

import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.embrace.android.embracesdk.fakes.FakeClock
import io.embrace.android.embracesdk.fakes.FakeConfigService
import io.embrace.android.embracesdk.fakes.FakeInstrumentationModule
import io.embrace.android.embracesdk.fakes.FakeInternalLogger
import io.embrace.android.embracesdk.fakes.FakeReadWriteLogRecord
import io.embrace.android.embracesdk.fakes.TestPlatformSerializer
import io.embrace.android.embracesdk.fakes.createSdkModeBehavior
import io.embrace.android.embracesdk.fakes.injection.FakeCoreModule
import io.embrace.android.embracesdk.fakes.injection.FakeInitModule
import io.embrace.android.embracesdk.internal.arch.InstrumentationRegistry
import io.embrace.android.embracesdk.internal.arch.datasource.DataSourceState
import io.embrace.android.embracesdk.internal.clock.Clock
import io.embrace.android.embracesdk.internal.config.PersistedConfig
import io.embrace.android.embracesdk.internal.config.remote.OtelKotlinSdkConfig
import io.embrace.android.embracesdk.internal.config.remote.RemoteConfig
import io.embrace.android.embracesdk.internal.injection.EssentialServiceModuleImpl
import io.embrace.android.embracesdk.internal.injection.InitModuleImpl
import io.embrace.android.embracesdk.internal.injection.ModuleInitBootstrapper
import io.embrace.android.embracesdk.internal.injection.postInit
import io.embrace.android.embracesdk.internal.injection.postLoadInstrumentation
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
internal class ModuleInitBootstrapperTest {

    private lateinit var moduleInitBootstrapper: ModuleInitBootstrapper
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
        val application = RuntimeEnvironment.getApplication()
        context = application.applicationContext
        moduleInitBootstrapper = ModuleInitBootstrapper(
            InitModuleImpl(logger, clock),
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
        with(moduleInitBootstrapper) {
            assertTrue(
                moduleInitBootstrapper.init(
                    context = context,
                ),
            )
            assertNotNull(initModule)
            assertNotNull(openTelemetryModule)
            assertNotNull(workerThreadModule)
            assertTrue(essentialServiceModule is EssentialServiceModuleImpl)
            assertNotNull(dataCaptureServiceModule)
            assertNotNull(deliveryModule)
            assertNotNull(payloadSourceModule)
            assertEquals(clock, moduleInitBootstrapper.initModule.clock)
        }
    }

    @Test
    fun `cannot initialize twice`() {
        assertTrue(
            moduleInitBootstrapper.init(
                context = context,
            ),
        )
        assertFalse(
            moduleInitBootstrapper.init(
                context = context,
            ),
        )
    }

    @Test
    fun `init returns normally and without failure`() {
        assertTrue(
            moduleInitBootstrapper.init(
                context = context,
            ),
        )
    }

    @Test
    fun `postInit installs metadata provider before instrumentation loads`() {
        assertTrue(moduleInitBootstrapper.init(context))
        val processor = moduleInitBootstrapper.openTelemetryModule.otelSdkConfig.logRecordProcessor

        val preLog = FakeReadWriteLogRecord()
        processor.onEmit(preLog, NoopOpenTelemetry.context.implicit())
        assertFalse(preLog.attributes.containsKey(EmbSessionAttributes.EMB_STATE))

        moduleInitBootstrapper.postInit()

        val postLog = FakeReadWriteLogRecord()
        processor.onEmit(postLog, NoopOpenTelemetry.context.implicit())
        assertTrue(postLog.attributes.containsKey(EmbSessionAttributes.EMB_STATE))
    }

    /**
     * The persisted config decides which OTel SDK implementation is built, so it has to be read
     * before that construction happens. Note that [ModuleGraph.postInit] is deliberately not called:
     * if the flag were still applied there, this would fail.
     */
    @Test
    fun `kotlin otel sdk selected from persisted config during init`() {
        val bootstrapper = createBootstrapperWithPersistedConfig(
            RemoteConfig(otelKotlinSdkConfig = OtelKotlinSdkConfig(pctEnabled = 100f)),
        )
        assertTrue(bootstrapper.init(context))
        assertTrue(bootstrapper.openTelemetryModule.otelSdkWrapper.useKotlinSdk)
    }

    @Test
    fun `kotlin otel sdk not selected when disabled by persisted config`() {
        val bootstrapper = createBootstrapperWithPersistedConfig(
            RemoteConfig(otelKotlinSdkConfig = OtelKotlinSdkConfig(pctEnabled = 0f)),
        )
        assertTrue(bootstrapper.init(context))
        assertFalse(bootstrapper.openTelemetryModule.otelSdkWrapper.useKotlinSdk)
    }

    /**
     * Guards the invariant that nothing constructs the OTel SDK before the config service exists.
     * Fails if OTel-touching work is reintroduced earlier in the module graph.
     */
    @Test
    fun `otel sdk is not built until the config service has been created`() {
        var initializedWhenConfigServiceBuilt: Boolean? = null
        val bootstrapper = ModuleInitBootstrapper(
            initModule = FakeInitModule(clock = clock, logger = logger),
            configServiceSupplier = { _, _, openTelemetryModule, _, _ ->
                initializedWhenConfigServiceBuilt = openTelemetryModule.spanService.initialized()
                FakeConfigService()
            },
            instrumentationModuleSupplier = { _, _, _, _, _, _, _, _, _ ->
                FakeInstrumentationModule(RuntimeEnvironment.getApplication(), logger = logger)
            },
        )
        assertTrue(bootstrapper.init(context))
        assertEquals(false, initializedWhenConfigServiceBuilt)
        assertTrue(bootstrapper.openTelemetryModule.spanService.initialized())
    }

    @Test
    fun `otel sdk is not built when the sdk is remotely disabled`() {
        val bootstrapper = ModuleInitBootstrapper(
            initModule = FakeInitModule(clock = clock, logger = logger),
            configServiceSupplier = { _, _, _, _, _ ->
                FakeConfigService(
                    sdkModeBehavior = createSdkModeBehavior(remoteCfg = RemoteConfig(threshold = 0)),
                )
            },
        )
        assertFalse(bootstrapper.init(context))
        assertFalse(bootstrapper.openTelemetryModule.spanService.initialized())
    }

    private fun createBootstrapperWithPersistedConfig(cfg: RemoteConfig): ModuleInitBootstrapper {
        val storageDir = File(context.filesDir, PersistedConfig.STORAGE_DIR_NAME).apply { mkdirs() }
        File(storageDir, "most_recent_response").outputStream().buffered().use { stream ->
            TestPlatformSerializer().toJson(cfg, RemoteConfig.serializer(), stream)
        }
        return ModuleInitBootstrapper(
            initModule = FakeInitModule(clock = clock, logger = logger),
            configServiceSupplier = { _, _, _, _, _ -> FakeConfigService() },
            instrumentationModuleSupplier = { _, _, _, _, _, _, _, _, _ ->
                FakeInstrumentationModule(RuntimeEnvironment.getApplication(), logger = logger)
            },
        )
    }

    @Test
    fun `post load instrumentation hooks up listeners`() {
        moduleInitBootstrapper.init(context)
        val registry = moduleInitBootstrapper.instrumentationModule.instrumentationRegistry
        val dataSource = CrashHandlerDataSource()
        registry.add(DataSourceState(factory = { dataSource }))

        moduleInitBootstrapper.postLoadInstrumentation()

        val handlers = dataSource.handlers
        val expected = listOf(
            moduleInitBootstrapper.threadBlockageService,
            moduleInitBootstrapper.logModule.logOrchestrator,
            moduleInitBootstrapper.userSessionOrchestrationModule.sessionOrchestrator,
            moduleInitBootstrapper.featureModule.crashMarker,
            moduleInitBootstrapper.deliveryModule?.payloadStore,
        )
        assertEquals(expected, handlers)
    }
}

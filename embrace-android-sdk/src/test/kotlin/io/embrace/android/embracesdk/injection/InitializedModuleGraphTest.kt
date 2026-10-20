package io.embrace.android.embracesdk.injection

import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.embrace.android.embracesdk.fakes.FakeClock
import io.embrace.android.embracesdk.fakes.FakeConfigService
import io.embrace.android.embracesdk.fakes.FakeErrorHandler
import io.embrace.android.embracesdk.fakes.FakeReadWriteLogRecord
import io.embrace.android.embracesdk.fakes.OtelSdkMode
import io.embrace.android.embracesdk.fakes.TestPlatformSerializer
import io.embrace.android.embracesdk.fakes.injection.FakeInitModule
import io.embrace.android.embracesdk.internal.clock.Clock
import io.embrace.android.embracesdk.internal.config.PersistedConfig
import io.embrace.android.embracesdk.internal.config.remote.OtelKotlinSdkConfig
import io.embrace.android.embracesdk.internal.config.remote.RemoteConfig
import io.embrace.android.embracesdk.internal.injection.ConfigLoadServices
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

    private lateinit var errorHandler: FakeErrorHandler
    private lateinit var clock: Clock
    private lateinit var context: Context

    @Before
    fun setup() {
        errorHandler = FakeErrorHandler(false)
        clock = FakeClock()
        context = RuntimeEnvironment.getApplication().applicationContext
    }

    private fun createModuleGraph(): InitializedModuleGraph {
        val preStartServices = PreStartServices(errorHandler, clock)
        return InitializedModuleGraph(
            context = context,
            preStartServices = preStartServices,
            configLoadServices = ConfigLoadServices(
                context = context,
                preStartServices = preStartServices,
                configServiceSupplier = { _, _, _, _, _, _ -> FakeConfigService() },
            ),
            sdkStartTimeMs = clock.now(),
            sdkInitResourceUsageTracker = SdkInitResourceUsageTracker(errorHandler),
        ).apply { start() }
    }

    @Test
    fun `test default implementation`() {
        with(createModuleGraph()) {
            assertNotNull(initModule)
            assertNotNull(spanService)
            assertNotNull(workerThreadPool)
            assertNotNull(processStateTracker)
            assertNotNull(startupTracker)
            assertNotNull(intakeService)
            assertNotNull(metadataService)
            assertEquals(clock, initModule.clock)
        }
    }

    @Test
    fun `metadata provider is installed before instrumentation loads`() {
        val moduleGraph = createModuleGraph()
        val processor = moduleGraph.otelSdkConfig.value.logRecordProcessor

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
        assertTrue(moduleGraph.otelSdkWrapper.value.useKotlinSdk)
    }

    @Test
    fun `kotlin otel sdk not selected when disabled by persisted config`() {
        val moduleGraph = createModuleGraphWithPersistedConfig(
            RemoteConfig(otelKotlinSdkConfig = OtelKotlinSdkConfig(pctEnabled = 0f)),
        )
        assertFalse(moduleGraph.otelSdkWrapper.value.useKotlinSdk)
    }

    private fun createModuleGraphWithPersistedConfig(cfg: RemoteConfig): InitializedModuleGraph {
        val storageDir = File(context.filesDir, PersistedConfig.STORAGE_DIR_NAME).apply { mkdirs() }
        File(storageDir, PersistedConfig.CONFIG_RESPONSE_FILE_NAME).outputStream().buffered().use { stream ->
            TestPlatformSerializer().toJson(cfg, RemoteConfig.serializer(), stream)
        }
        val initModule = FakeInitModule(clock = clock, errorHandler = errorHandler, otelSdkMode = OtelSdkMode.COMPAT)
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
            sdkInitResourceUsageTracker = SdkInitResourceUsageTracker(errorHandler),
            initModuleSupplier = { initModule },
        )
    }

    @Test
    fun `post load instrumentation hooks up listeners`() {
        val moduleGraph = createModuleGraph()
        val registry = moduleGraph.instrumentationRegistry
        val dataSource = CrashHandlerDataSource()
        registry.add { dataSource }

        createRealSdkInstance(moduleGraph, clock).postLoadInstrumentation()

        val handlers = dataSource.handlers
        val expected = listOf(
            moduleGraph.threadBlockageService,
            moduleGraph.logOrchestrator,
            moduleGraph.sessionOrchestrator,
            moduleGraph.crashMarker,
            moduleGraph.payloadStore,
        )
        assertEquals(expected, handlers)
    }
}

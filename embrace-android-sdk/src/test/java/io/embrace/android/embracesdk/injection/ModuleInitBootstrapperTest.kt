package io.embrace.android.embracesdk.injection

import android.content.Context
import android.os.Build.VERSION_CODES.TIRAMISU
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.embrace.android.embracesdk.fakes.FakeClock
import io.embrace.android.embracesdk.fakes.FakeConfigModule
import io.embrace.android.embracesdk.fakes.FakeConfigService
import io.embrace.android.embracesdk.fakes.FakeEmbLogger
import io.embrace.android.embracesdk.fakes.FakeInstrumentationModule
import io.embrace.android.embracesdk.fakes.FakeNativeFeatureModule
import io.embrace.android.embracesdk.fakes.injection.FakeCoreModule
import io.embrace.android.embracesdk.internal.arch.InstrumentationRegistry
import io.embrace.android.embracesdk.internal.arch.datasource.DataSourceState
import io.embrace.android.embracesdk.internal.clock.Clock
import io.embrace.android.embracesdk.internal.injection.EssentialServiceModuleImpl
import io.embrace.android.embracesdk.internal.injection.InitModuleImpl
import io.embrace.android.embracesdk.internal.injection.ModuleInitBootstrapper
import io.embrace.android.embracesdk.internal.injection.postLoadInstrumentation
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@Config(sdk = [TIRAMISU])
@RunWith(AndroidJUnit4::class)
internal class ModuleInitBootstrapperTest {

    private lateinit var moduleInitBootstrapper: ModuleInitBootstrapper
    private lateinit var logger: FakeEmbLogger
    private lateinit var clock: Clock
    private lateinit var coreModule: FakeCoreModule
    private lateinit var context: Context
    private lateinit var registry: InstrumentationRegistry

    @Before
    fun setup() {
        logger = FakeEmbLogger(false)
        clock = FakeClock()
        coreModule = FakeCoreModule()
        val application = RuntimeEnvironment.getApplication()
        context = application.applicationContext
        moduleInitBootstrapper = ModuleInitBootstrapper(
            InitModuleImpl(logger, clock),
            configModuleSupplier = { _, _, _, _ -> FakeConfigModule(FakeConfigService()) },
            coreModuleSupplier = { _, _ -> coreModule },
            nativeFeatureModuleSupplier = { _, _ -> FakeNativeFeatureModule() },
            instrumentationModuleSupplier = { _, _, _, _, _, _ ->
                FakeInstrumentationModule(application, logger = logger).apply {
                    registry = instrumentationRegistry
                }
            }
        )
    }

    @Test
    fun `test default implementation`() {
        with(moduleInitBootstrapper) {
            assertTrue(
                moduleInitBootstrapper.init(
                    context = context,
                )
            )
            assertNotNull(initModule)
            assertNotNull(openTelemetryModule)
            assertNotNull(workerThreadModule)
            assertNotNull(storageModule)
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
            )
        )
        assertFalse(
            moduleInitBootstrapper.init(
                context = context,
            )
        )
    }

    @Test
    fun `init returns normally and without failure`() {
        assertTrue(
            moduleInitBootstrapper.init(
                context = context,
            )
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
            moduleInitBootstrapper.anrModule.anrService,
            moduleInitBootstrapper.logModule.logOrchestrator,
            moduleInitBootstrapper.sessionOrchestrationModule.sessionOrchestrator,
            moduleInitBootstrapper.featureModule.crashMarker,
            moduleInitBootstrapper.deliveryModule.payloadStore
        )
        assertEquals(expected, handlers)
    }
}

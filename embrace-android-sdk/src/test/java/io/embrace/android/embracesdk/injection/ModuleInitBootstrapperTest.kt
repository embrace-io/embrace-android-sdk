package io.embrace.android.embracesdk.injection

import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.embrace.android.embracesdk.fakes.FakeConfigModule
import io.embrace.android.embracesdk.fakes.FakeConfigService
import io.embrace.android.embracesdk.fakes.FakeNativeFeatureModule
import io.embrace.android.embracesdk.fakes.injection.FakeCoreModule
import io.embrace.android.embracesdk.internal.injection.EssentialServiceModuleImpl
import io.embrace.android.embracesdk.internal.injection.ModuleInitBootstrapper
import io.embrace.android.embracesdk.internal.logging.EmbLogger
import io.embrace.android.embracesdk.internal.logging.EmbLoggerImpl
import io.embrace.android.embracesdk.internal.payload.AppFramework
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RuntimeEnvironment

@RunWith(AndroidJUnit4::class)
internal class ModuleInitBootstrapperTest {

    private lateinit var moduleInitBootstrapper: ModuleInitBootstrapper
    private lateinit var logger: EmbLogger
    private lateinit var coreModule: FakeCoreModule
    private lateinit var context: Context

    @Before
    fun setup() {
        logger = EmbLoggerImpl()
        coreModule = FakeCoreModule()
        moduleInitBootstrapper = ModuleInitBootstrapper(
            configModuleSupplier = { _, _, _, _, _, _, _, _ -> FakeConfigModule(FakeConfigService()) },
            coreModuleSupplier = { _ -> coreModule },
            nativeFeatureModuleSupplier = { _, _, _, _, _, _, _, _, _ -> FakeNativeFeatureModule() },
            logger = logger
        )
        context = RuntimeEnvironment.getApplication().applicationContext
    }

    @Test
    fun `test default implementation`() {
        with(moduleInitBootstrapper) {
            assertTrue(
                moduleInitBootstrapper.init(
                    context = context,
                    appFramework = AppFramework.NATIVE,
                    sdkStartTimeMs = 0L,
                )
            )
            assertNotNull(initModule)
            assertNotNull(openTelemetryModule)
            assertNotNull(workerThreadModule)
            assertNotNull(systemServiceModule)
            assertNotNull(androidServicesModule)
            assertNotNull(storageModule)
            assertTrue(essentialServiceModule is EssentialServiceModuleImpl)
            assertNotNull(dataCaptureServiceModule)
            assertNotNull(deliveryModule)
            assertNotNull(payloadSourceModule)
        }
    }

    @Test
    fun `cannot initialize twice`() {
        assertTrue(
            moduleInitBootstrapper.init(
                context = context,
                appFramework = AppFramework.NATIVE,
                sdkStartTimeMs = 0L,
            )
        )
        assertFalse(
            moduleInitBootstrapper.init(
                context = context,
                appFramework = AppFramework.NATIVE,
                sdkStartTimeMs = 0L,
            )
        )
    }

    @Test
    fun `init returns normally and without failure`() {
        assertTrue(
            moduleInitBootstrapper.init(
                context = context,
                appFramework = AppFramework.NATIVE,
                sdkStartTimeMs = 0L,
            )
        )
    }

    @Test
    fun `stopping services makes bootstrapper not initialized`() {
        assertTrue(
            moduleInitBootstrapper.init(
                context = context,
                appFramework = AppFramework.NATIVE,
                sdkStartTimeMs = 0L,
            )
        )

        assertTrue(moduleInitBootstrapper.isInitialized())
        moduleInitBootstrapper.stopServices()
        assertFalse(moduleInitBootstrapper.isInitialized())
    }
}

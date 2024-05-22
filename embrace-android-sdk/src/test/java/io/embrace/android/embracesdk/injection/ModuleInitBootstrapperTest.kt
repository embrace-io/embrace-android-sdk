package io.embrace.android.embracesdk.injection

import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.embrace.android.embracesdk.Embrace
import io.embrace.android.embracesdk.fakes.FakeClock
import io.embrace.android.embracesdk.fakes.injection.FakeCoreModule
import io.embrace.android.embracesdk.fakes.injection.FakeInitModule
import io.embrace.android.embracesdk.fakes.injection.FakeWorkerThreadModule
import io.embrace.android.embracesdk.logging.EmbLogger
import io.embrace.android.embracesdk.logging.EmbLoggerImpl
import io.embrace.android.embracesdk.worker.WorkerName
import io.embrace.android.embracesdk.worker.WorkerThreadModuleImpl
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RuntimeEnvironment
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException

@RunWith(AndroidJUnit4::class)
internal class ModuleInitBootstrapperTest {

    private lateinit var moduleInitBootstrapper: ModuleInitBootstrapper
    private lateinit var logger: EmbLogger
    private lateinit var coreModule: FakeCoreModule
    private lateinit var context: Context

    @Before
    fun setup() {
        logger = EmbLoggerImpl()
        coreModule = FakeCoreModule(logger = logger)
        moduleInitBootstrapper = ModuleInitBootstrapper(coreModuleSupplier = { _, _, _ -> coreModule }, logger = logger)
        context = RuntimeEnvironment.getApplication().applicationContext
    }

    @Test
    fun `test default implementation`() {
        val moduleInitBootstrapper = ModuleInitBootstrapper(
            coreModuleSupplier = { _, _, _ -> coreModule },
            logger = EmbLoggerImpl()
        )
        with(moduleInitBootstrapper) {
            assertTrue(
                moduleInitBootstrapper.init(
                    context = context,
                    appFramework = Embrace.AppFramework.NATIVE,
                    sdkStartTimeMs = 0L,
                )
            )
            assertTrue(initModule is InitModuleImpl)
            assertTrue(openTelemetryModule is OpenTelemetryModuleImpl)
            assertTrue(workerThreadModule is WorkerThreadModuleImpl)
            assertTrue(systemServiceModule is SystemServiceModuleImpl)
            assertTrue(androidServicesModule is AndroidServicesModuleImpl)
            assertTrue(storageModule is StorageModuleImpl)
            assertTrue(essentialServiceModule is EssentialServiceModuleImpl)
            assertTrue(dataCaptureServiceModule is DataCaptureServiceModuleImpl)
            assertTrue(deliveryModule is DeliveryModuleImpl)
            assertTrue(payloadModule is PayloadModuleImpl)
        }
    }

    @Test
    fun `cannot initialize twice`() {
        assertTrue(
            moduleInitBootstrapper.init(
                context = context,
                appFramework = Embrace.AppFramework.NATIVE,
                sdkStartTimeMs = 0L,
            )
        )
        assertFalse(
            moduleInitBootstrapper.init(
                context = context,
                appFramework = Embrace.AppFramework.NATIVE,
                sdkStartTimeMs = 0L,
            )
        )
    }

    @Test
    fun `async init returns normally and without failure`() {
        assertTrue(
            moduleInitBootstrapper.init(
                context = context,
                appFramework = Embrace.AppFramework.NATIVE,
                sdkStartTimeMs = 0L,
            )
        )
        moduleInitBootstrapper.waitForAsyncInit()
    }

    @Test
    fun `async init throws exception if it waiting for too long`() {
        val fakeInitModule = FakeInitModule(clock = FakeClock())
        val fakeCoreModule = FakeCoreModule(logger = logger)
        val fakeWorkerThreadModule = FakeWorkerThreadModule(
            fakeInitModule = fakeInitModule,
            name = WorkerName.BACKGROUND_REGISTRATION
        )
        val bootstrapper = ModuleInitBootstrapper(
            initModule = fakeInitModule,
            coreModuleSupplier = { _, _, _ -> fakeCoreModule },
            workerThreadModuleSupplier = { _ -> fakeWorkerThreadModule },
            logger = EmbLoggerImpl()
        )
        assertTrue(
            bootstrapper.init(
                context = context,
                appFramework = Embrace.AppFramework.NATIVE,
                sdkStartTimeMs = 0L,
            )
        )
        assertThrows(TimeoutException::class.java) {
            bootstrapper.waitForAsyncInit(500L, TimeUnit.MILLISECONDS)
        }
    }

    @Test
    fun `stopping services makes bootstrapper not initialized`() {
        assertTrue(
            moduleInitBootstrapper.init(
                context = context,
                appFramework = Embrace.AppFramework.NATIVE,
                sdkStartTimeMs = 0L,
            )
        )

        assertTrue(moduleInitBootstrapper.isInitialized())
        moduleInitBootstrapper.stopServices()
        assertFalse(moduleInitBootstrapper.isInitialized())
    }
}

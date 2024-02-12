package io.embrace.android.embracesdk.injection

import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.embrace.android.embracesdk.Embrace
import io.embrace.android.embracesdk.fakes.FakeClock
import io.embrace.android.embracesdk.fakes.injection.FakeCoreModule
import io.embrace.android.embracesdk.fakes.injection.FakeInitModule
import io.embrace.android.embracesdk.session.FakeWorkerThreadModule
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
    private lateinit var context: Context

    @Before
    fun setup() {
        moduleInitBootstrapper = ModuleInitBootstrapper(coreModuleSupplier = { _, _ -> FakeCoreModule() })
        context = RuntimeEnvironment.getApplication().applicationContext
    }

    @Test
    fun `test default implementation`() {
        val moduleInitBootstrapper = ModuleInitBootstrapper(coreModuleSupplier = { _, _ -> FakeCoreModule() })
        with(moduleInitBootstrapper) {
            assertTrue(moduleInitBootstrapper.init(context, false, Embrace.AppFramework.NATIVE, 0L))
            assertTrue(initModule is InitModuleImpl)
            assertTrue(openTelemetryModule is OpenTelemetryModuleImpl)
            assertTrue(workerThreadModule is WorkerThreadModuleImpl)
            assertTrue(systemServiceModule is SystemServiceModuleImpl)
            assertTrue(androidServicesModule is AndroidServicesModuleImpl)
            assertTrue(storageModule is StorageModuleImpl)
            assertTrue(essentialServiceModule is EssentialServiceModuleImpl)
            assertTrue(dataCaptureServiceModule is DataCaptureServiceModuleImpl)
            assertTrue(deliveryModule is DeliveryModuleImpl)
        }
    }

    @Test
    fun `cannot initialize twice`() {
        assertTrue(moduleInitBootstrapper.init(context, false, Embrace.AppFramework.NATIVE, 0L))
        assertFalse(moduleInitBootstrapper.init(context, false, Embrace.AppFramework.NATIVE, 0L))
    }

    @Test
    fun `async init returns normally and without failure`() {
        assertTrue(moduleInitBootstrapper.init(context, false, Embrace.AppFramework.NATIVE, 0L))
        moduleInitBootstrapper.waitForAsyncInit()
    }

    @Test
    fun `async init throws exception if it waiting for too long`() {
        val fakeClock = FakeClock()
        val fakeInitModule = FakeInitModule(clock = fakeClock)
        val fakeCoreModule = FakeCoreModule()
        val fakeWorkerThreadModule =
            FakeWorkerThreadModule(fakeInitModule = fakeInitModule, name = WorkerName.BACKGROUND_REGISTRATION)
        val bootstrapper = ModuleInitBootstrapper(
            initModule = fakeInitModule,
            coreModuleSupplier = { _, _ -> fakeCoreModule },
            workerThreadModuleSupplier = { _ -> fakeWorkerThreadModule }
        )
        assertTrue(bootstrapper.init(context, false, Embrace.AppFramework.NATIVE, 0L))
        assertThrows(TimeoutException::class.java) {
            bootstrapper.waitForAsyncInit(500L, TimeUnit.MILLISECONDS)
        }
    }
}

package io.embrace.android.embracesdk.injection

import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.embrace.android.embracesdk.Embrace
import io.embrace.android.embracesdk.fakes.injection.FakeCoreModule
import io.embrace.android.embracesdk.worker.WorkerThreadModuleImpl
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RuntimeEnvironment

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
            assertTrue(moduleInitBootstrapper.init(context, false, Embrace.AppFramework.NATIVE))
            assertTrue(initModule is InitModuleImpl)
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
        assertTrue(moduleInitBootstrapper.init(context, false, Embrace.AppFramework.NATIVE))
        assertFalse(moduleInitBootstrapper.init(context, false, Embrace.AppFramework.NATIVE))
    }
}

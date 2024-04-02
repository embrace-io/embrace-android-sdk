package io.embrace.android.embracesdk.injection

import io.embrace.android.embracesdk.fakes.injection.FakeCoreModule
import io.embrace.android.embracesdk.fakes.injection.FakeEssentialServiceModule
import io.embrace.android.embracesdk.fakes.injection.FakeInitModule
import io.embrace.android.embracesdk.fakes.injection.FakeStorageModule
import io.embrace.android.embracesdk.worker.WorkerThreadModuleImpl
import org.junit.Assert.assertNotNull
import org.junit.Test

internal class DeliveryModuleImplTest {

    @Test
    fun testDefaultImplementations() {
        val initModule = FakeInitModule()
        val coreModule = FakeCoreModule()
        val module = DeliveryModuleImpl(
            initModule,
            coreModule,
            WorkerThreadModuleImpl(FakeInitModule()),
            FakeStorageModule(),
            FakeEssentialServiceModule(),
        )

        assertNotNull(module.deliveryService)
    }
}

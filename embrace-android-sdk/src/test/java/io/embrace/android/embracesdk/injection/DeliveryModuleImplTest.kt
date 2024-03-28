package io.embrace.android.embracesdk.injection

import io.embrace.android.embracesdk.fakes.injection.FakeCoreModule
import io.embrace.android.embracesdk.fakes.injection.FakeEssentialServiceModule
import io.embrace.android.embracesdk.fakes.injection.FakeStorageModule
import io.embrace.android.embracesdk.worker.WorkerThreadModuleImpl
import org.junit.Assert.assertNotNull
import org.junit.Test

internal class DeliveryModuleImplTest {

    @Test
    fun testDefaultImplementations() {
        val fakeCoreModule = FakeCoreModule()
        val module = DeliveryModuleImpl(
            fakeCoreModule,
            WorkerThreadModuleImpl(InitModuleImpl(), fakeCoreModule),
            FakeStorageModule(),
            FakeEssentialServiceModule(),
        )

        assertNotNull(module.deliveryService)
    }
}

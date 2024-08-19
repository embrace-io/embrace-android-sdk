package io.embrace.android.embracesdk.injection

import io.embrace.android.embracesdk.fakes.FakeApiService
import io.embrace.android.embracesdk.fakes.injection.FakeInitModule
import io.embrace.android.embracesdk.fakes.injection.FakeStorageModule
import io.embrace.android.embracesdk.fakes.injection.FakeWorkerThreadModule
import io.embrace.android.embracesdk.internal.injection.createDeliveryModule
import org.junit.Assert.assertNotNull
import org.junit.Test

internal class DeliveryModuleImplTest {

    @Test
    fun testDefaultImplementations() {
        val initModule = FakeInitModule()
        val module = createDeliveryModule(
            initModule,
            FakeWorkerThreadModule(),
            FakeStorageModule(),
            FakeApiService(),
        )

        assertNotNull(module.deliveryService)
    }
}

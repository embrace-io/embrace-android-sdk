package io.embrace.android.embracesdk.injection

import io.embrace.android.embracesdk.FakeWorkerThreadModule
import io.embrace.android.embracesdk.fakes.injection.FakeCoreModule
import io.embrace.android.embracesdk.fakes.injection.FakeDataCaptureServiceModule
import io.embrace.android.embracesdk.fakes.injection.FakeEssentialServiceModule
import org.junit.Assert.assertNotNull
import org.junit.Test

internal class DeliveryModuleImplTest {

    @Test
    fun testDefaultImplementations() {
        val module = DeliveryModuleImpl(
            InitModuleImpl(),
            FakeCoreModule(),
            FakeEssentialServiceModule(),
            FakeDataCaptureServiceModule(),
            FakeWorkerThreadModule()
        )

        assertNotNull(module.deliveryService)
        assertNotNull(module.deliveryCacheManager)
        assertNotNull(module.cacheService)
    }
}

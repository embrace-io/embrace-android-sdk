package io.embrace.android.embracesdk.injection

import io.embrace.android.embracesdk.FakeWorkerThreadModule
import io.embrace.android.embracesdk.fakes.injection.FakeCoreModule
import io.embrace.android.embracesdk.fakes.injection.FakeEssentialServiceModule
import org.junit.Assert.assertNotNull
import org.junit.Test

internal class DeliveryModuleImplTest {

    @Test
    fun testDefaultImplementations() {
        val module = DeliveryModuleImpl(
            FakeCoreModule(),
            FakeEssentialServiceModule(),
            FakeWorkerThreadModule()
        )

        assertNotNull(module.deliveryService)
    }
}

package io.embrace.android.embracesdk.injection

import io.embrace.android.embracesdk.fakes.injection.FakeEssentialServiceModule
import io.embrace.android.embracesdk.fakes.injection.FakeInitModule
import io.embrace.android.embracesdk.fakes.injection.FakeStorageModule
import io.embrace.android.embracesdk.internal.worker.WorkerThreadModuleImpl
import org.junit.Assert.assertNotNull
import org.junit.Test

internal class DeliveryModuleImplTest {

    @Test
    fun testDefaultImplementations() {
        val initModule = FakeInitModule()
        val module = DeliveryModuleImpl(
            initModule,
            WorkerThreadModuleImpl(InitModuleImpl()),
            FakeStorageModule(),
            FakeEssentialServiceModule(),
        )

        assertNotNull(module.deliveryService)
    }
}

package io.embrace.android.embracesdk.injection

import io.embrace.android.embracesdk.fakes.injection.FakeDeliveryModule
import io.embrace.android.embracesdk.fakes.injection.FakeEssentialServiceModule
import io.embrace.android.embracesdk.fakes.injection.FakeInitModule
import io.embrace.android.embracesdk.fakes.injection.FakeWorkerThreadModule
import org.junit.Assert.assertNotNull
import org.junit.Test

internal class DataContainerModuleImplTest {

    @Test
    fun testDefaultImplementations() {
        val module = DataContainerModuleImpl(
            FakeInitModule(),
            FakeWorkerThreadModule(),
            FakeEssentialServiceModule(),
            FakeDeliveryModule(),
            0
        )
        assertNotNull(module.eventService)
        assertNotNull(module.performanceInfoService)
    }
}

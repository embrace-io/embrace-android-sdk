package io.embrace.android.embracesdk.injection

import io.embrace.android.embracesdk.FakeWorkerThreadModule
import io.embrace.android.embracesdk.fakes.fakeEmbraceSessionProperties
import io.embrace.android.embracesdk.fakes.injection.FakeAndroidServicesModule
import io.embrace.android.embracesdk.fakes.injection.FakeCoreModule
import io.embrace.android.embracesdk.fakes.injection.FakeDeliveryModule
import io.embrace.android.embracesdk.fakes.injection.FakeEssentialServiceModule
import org.junit.Assert.assertNotNull
import org.junit.Test

internal class CustomerLogModuleImplTest {

    @Test
    fun testDefaultImplementations() {
        val module = CustomerLogModuleImpl(
            InitModuleImpl(),
            FakeCoreModule(),
            FakeAndroidServicesModule(),
            FakeEssentialServiceModule(),
            FakeDeliveryModule(),
            fakeEmbraceSessionProperties(),
            FakeWorkerThreadModule()
        )

        assertNotNull(module.networkCaptureService)
        assertNotNull(module.networkLoggingService)
        assertNotNull(module.remoteLogger)
    }
}

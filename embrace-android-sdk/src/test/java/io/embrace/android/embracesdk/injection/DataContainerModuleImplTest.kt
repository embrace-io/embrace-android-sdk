package io.embrace.android.embracesdk.injection

import io.embrace.android.embracesdk.fakes.FakeOpenTelemetryModule
import io.embrace.android.embracesdk.fakes.injection.FakeAndroidServicesModule
import io.embrace.android.embracesdk.fakes.injection.FakeAnrModule
import io.embrace.android.embracesdk.fakes.injection.FakeCustomerLogModule
import io.embrace.android.embracesdk.fakes.injection.FakeDataCaptureServiceModule
import io.embrace.android.embracesdk.fakes.injection.FakeDeliveryModule
import io.embrace.android.embracesdk.fakes.injection.FakeEssentialServiceModule
import io.embrace.android.embracesdk.fakes.injection.FakeInitModule
import io.embrace.android.embracesdk.fakes.injection.FakeNativeModule
import io.embrace.android.embracesdk.fakes.injection.FakeSystemServiceModule
import io.embrace.android.embracesdk.fakes.injection.FakeWorkerThreadModule
import org.junit.Assert.assertNotNull
import org.junit.Test

internal class DataContainerModuleImplTest {

    @Test
    fun testDefaultImplementations() {
        val module = DataContainerModuleImpl(
            FakeInitModule(),
            FakeOpenTelemetryModule(),
            FakeWorkerThreadModule(),
            FakeSystemServiceModule(),
            FakeAndroidServicesModule(),
            FakeEssentialServiceModule(),
            FakeDataCaptureServiceModule(),
            FakeAnrModule(),
            FakeCustomerLogModule(),
            FakeDeliveryModule(),
            FakeNativeModule(),
            0
        )
        assertNotNull(module.eventService)
        assertNotNull(module.performanceInfoService)
    }
}

package io.embrace.android.embracesdk.injection

import io.embrace.android.embracesdk.fakes.fakeEmbraceSessionProperties
import io.embrace.android.embracesdk.fakes.injection.FakeAnrModule
import io.embrace.android.embracesdk.fakes.injection.FakeCoreModule
import io.embrace.android.embracesdk.fakes.injection.FakeCustomerLogModule
import io.embrace.android.embracesdk.fakes.injection.FakeDataCaptureServiceModule
import io.embrace.android.embracesdk.fakes.injection.FakeDeliveryModule
import io.embrace.android.embracesdk.fakes.injection.FakeEssentialServiceModule
import io.embrace.android.embracesdk.fakes.injection.FakeInitModule
import io.embrace.android.embracesdk.fakes.injection.FakeNativeModule
import io.embrace.android.embracesdk.worker.WorkerThreadModuleImpl
import org.junit.Assert.assertNotNull
import org.junit.Test

internal class DataContainerModuleImplTest {

    @Test
    fun testDefaultImplementations() {
        val initModule = FakeInitModule()
        val module = DataContainerModuleImpl(
            initModule,
            initModule.openTelemetryModule,
            FakeCoreModule(),
            WorkerThreadModuleImpl(initModule),
            FakeEssentialServiceModule(),
            FakeDataCaptureServiceModule(),
            FakeAnrModule(),
            FakeCustomerLogModule(),
            FakeDeliveryModule(),
            FakeNativeModule(),
            fakeEmbraceSessionProperties(),
            0
        )
        assertNotNull(module.eventService)
        assertNotNull(module.performanceInfoService)
    }
}

package io.embrace.android.embracesdk.injection

import io.embrace.android.embracesdk.fakes.FakeOpenTelemetryModule
import io.embrace.android.embracesdk.fakes.FakePayloadModule
import io.embrace.android.embracesdk.fakes.fakeEmbraceSessionProperties
import io.embrace.android.embracesdk.fakes.injection.FakeAndroidServicesModule
import io.embrace.android.embracesdk.fakes.injection.FakeCoreModule
import io.embrace.android.embracesdk.fakes.injection.FakeDeliveryModule
import io.embrace.android.embracesdk.fakes.injection.FakeEssentialServiceModule
import io.embrace.android.embracesdk.worker.WorkerThreadModuleImpl
import org.junit.Assert.assertNotNull
import org.junit.Test

internal class CustomerLogModuleImplTest {

    @Test
    fun testDefaultImplementations() {
        val initModule = InitModuleImpl()
        val fakeCoreModule = FakeCoreModule()
        val module = CustomerLogModuleImpl(
            initModule,
            fakeCoreModule,
            FakeOpenTelemetryModule(),
            FakeAndroidServicesModule(),
            FakeEssentialServiceModule(),
            FakeDeliveryModule(),
            fakeEmbraceSessionProperties(),
            WorkerThreadModuleImpl(initModule),
            FakePayloadModule(),
        )

        assertNotNull(module.networkCaptureService)
        assertNotNull(module.networkLoggingService)
        assertNotNull(module.logMessageService)
    }
}

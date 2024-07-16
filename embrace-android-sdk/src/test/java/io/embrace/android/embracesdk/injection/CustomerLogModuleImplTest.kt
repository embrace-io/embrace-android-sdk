package io.embrace.android.embracesdk.injection

import io.embrace.android.embracesdk.fakes.FakeOpenTelemetryModule
import io.embrace.android.embracesdk.fakes.FakePayloadModule
import io.embrace.android.embracesdk.fakes.injection.FakeAndroidServicesModule
import io.embrace.android.embracesdk.fakes.injection.FakeDeliveryModule
import io.embrace.android.embracesdk.fakes.injection.FakeEssentialServiceModule
import io.embrace.android.embracesdk.internal.injection.CustomerLogModuleImpl
import io.embrace.android.embracesdk.internal.injection.InitModuleImpl
import io.embrace.android.embracesdk.internal.worker.WorkerThreadModuleImpl
import org.junit.Assert.assertNotNull
import org.junit.Test

internal class CustomerLogModuleImplTest {

    @Test
    fun testDefaultImplementations() {
        val initModule = InitModuleImpl()
        val module = CustomerLogModuleImpl(
            initModule,
            FakeOpenTelemetryModule(),
            FakeAndroidServicesModule(),
            FakeEssentialServiceModule(),
            FakeDeliveryModule(),
            WorkerThreadModuleImpl(initModule),
            FakePayloadModule(),
        )

        assertNotNull(module.networkCaptureService)
        assertNotNull(module.networkLoggingService)
        assertNotNull(module.logService)
    }
}

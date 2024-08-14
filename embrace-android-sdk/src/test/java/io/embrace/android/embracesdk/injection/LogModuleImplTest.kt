package io.embrace.android.embracesdk.injection

import io.embrace.android.embracesdk.fakes.FakeOpenTelemetryModule
import io.embrace.android.embracesdk.fakes.FakePayloadSourceModule
import io.embrace.android.embracesdk.fakes.injection.FakeAndroidServicesModule
import io.embrace.android.embracesdk.fakes.injection.FakeDeliveryModule
import io.embrace.android.embracesdk.fakes.injection.FakeEssentialServiceModule
import io.embrace.android.embracesdk.fakes.injection.FakeInitModule
import io.embrace.android.embracesdk.fakes.injection.FakeWorkerThreadModule
import io.embrace.android.embracesdk.internal.injection.LogModuleImpl
import org.junit.Assert.assertNotNull
import org.junit.Test

internal class LogModuleImplTest {

    @Test
    fun testDefaultImplementations() {
        val initModule = FakeInitModule()
        val module = LogModuleImpl(
            initModule,
            FakeOpenTelemetryModule(),
            FakeAndroidServicesModule(),
            FakeEssentialServiceModule(),
            FakeDeliveryModule(),
            FakeWorkerThreadModule(),
            FakePayloadSourceModule(),
        )

        assertNotNull(module.networkCaptureService)
        assertNotNull(module.networkLoggingService)
        assertNotNull(module.logService)
    }
}

package io.embrace.android.embracesdk.internal.injection

import io.embrace.android.embracesdk.fakes.FakeConfigModule
import io.embrace.android.embracesdk.fakes.FakeDeliveryModule
import io.embrace.android.embracesdk.fakes.FakeOpenTelemetryModule
import io.embrace.android.embracesdk.fakes.injection.FakeEssentialServiceModule
import io.embrace.android.embracesdk.fakes.injection.FakeInitModule
import io.embrace.android.embracesdk.fakes.injection.FakePayloadSourceModule
import io.embrace.android.embracesdk.fakes.injection.FakeWorkerThreadModule
import org.junit.Assert.assertNotNull
import org.junit.Test

internal class LogModuleImplTest {

    @Test
    fun testDefaultImplementations() {
        val initModule = FakeInitModule()
        val module = LogModuleImpl(
            initModule,
            FakeOpenTelemetryModule(),
            FakeEssentialServiceModule(),
            FakeConfigModule(),
            FakeDeliveryModule(),
            FakeWorkerThreadModule(),
            FakePayloadSourceModule(),
        )

        assertNotNull(module.logService)
        assertNotNull(module.attachmentService)
    }
}

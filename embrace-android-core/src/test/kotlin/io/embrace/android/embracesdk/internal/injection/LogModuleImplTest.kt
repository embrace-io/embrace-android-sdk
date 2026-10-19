package io.embrace.android.embracesdk.internal.injection

import io.embrace.android.embracesdk.fakes.FakeConfigService
import io.embrace.android.embracesdk.fakes.FakeDeliveryModule
import io.embrace.android.embracesdk.fakes.FakeOpenTelemetryModule
import io.embrace.android.embracesdk.fakes.OtelSdkMode
import io.embrace.android.embracesdk.fakes.injection.FakeEssentialServiceModule
import io.embrace.android.embracesdk.fakes.injection.FakeInitModule
import io.embrace.android.embracesdk.fakes.injection.FakePayloadSourceModule
import io.embrace.android.embracesdk.fakes.injection.FakeWorkerThreadPool
import org.junit.Assert.assertNotNull
import org.junit.Test

internal class LogModuleImplTest {

    @Test
    fun testDefaultImplementations() {
        val initModule = FakeInitModule(otelSdkMode = OtelSdkMode.COMPAT)
        val module = LogModuleImpl(
            initModule,
            FakeOpenTelemetryModule(otelSdkMode = OtelSdkMode.REGULAR),
            FakeEssentialServiceModule(),
            FakeConfigService(),
            FakeDeliveryModule(),
            FakeWorkerThreadPool(),
            FakePayloadSourceModule(),
        )

        assertNotNull(module.logService)
        assertNotNull(module.attachmentService)
    }
}

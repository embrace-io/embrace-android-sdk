package io.embrace.android.embracesdk.internal.delivery

import io.embrace.android.embracesdk.fakes.FakeConfigModule
import io.embrace.android.embracesdk.fakes.FakeConfigService
import io.embrace.android.embracesdk.fakes.injection.FakeInitModule
import io.embrace.android.embracesdk.fakes.injection.FakeWorkerThreadModule
import io.embrace.android.embracesdk.internal.injection.DeliveryModule2Impl
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class DeliveryModule2ImplTest {

    @Test
    fun testModule() {
        val module = DeliveryModule2Impl(
            FakeConfigModule(),
            FakeInitModule(),
            FakeWorkerThreadModule()
        )
        assertNotNull(module)
        assertNotNull(module.intakeService)
        assertNotNull(module.payloadCachingService)
        assertNotNull(module.payloadResurrectionService)
        assertNotNull(module.requestExecutionService)
        assertNotNull(module.schedulingService)
    }

    @Test
    fun `test otel export only`() {
        val module = DeliveryModule2Impl(
            FakeConfigModule(configService = FakeConfigService(onlyUsingOtelExporters = true)),
            FakeInitModule(),
            FakeWorkerThreadModule()
        )
        assertNotNull(module)
        assertNull(module.intakeService)
        assertNull(module.payloadCachingService)
        assertNull(module.payloadResurrectionService)
        assertNull(module.requestExecutionService)
        assertNull(module.schedulingService)
    }
}

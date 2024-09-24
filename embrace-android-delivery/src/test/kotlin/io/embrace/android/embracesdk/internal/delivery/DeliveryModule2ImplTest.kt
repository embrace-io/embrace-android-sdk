package io.embrace.android.embracesdk.internal.delivery

import io.embrace.android.embracesdk.fakes.FakeConfigModule
import io.embrace.android.embracesdk.fakes.FakeConfigService
import io.embrace.android.embracesdk.fakes.injection.FakeInitModule
import io.embrace.android.embracesdk.fakes.injection.FakeStorageModule
import io.embrace.android.embracesdk.fakes.injection.FakeWorkerThreadModule
import io.embrace.android.embracesdk.internal.injection.DeliveryModule2
import io.embrace.android.embracesdk.internal.injection.DeliveryModule2Impl
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

class DeliveryModule2ImplTest {

    private lateinit var module: DeliveryModule2
    private lateinit var configService: FakeConfigService

    @Before
    fun setUp() {
        configService = FakeConfigService()
        module = DeliveryModule2Impl(
            FakeConfigModule(configService),
            FakeInitModule(),
            FakeWorkerThreadModule(),
            FakeStorageModule()
        )
    }

    @Test
    fun testModule() {
        assertNotNull(module)
        assertNotNull(module.intakeService)
        assertNotNull(module.payloadCachingService)
        assertNotNull(module.payloadStorageService)
        assertNotNull(module.payloadResurrectionService)
        assertNotNull(module.requestExecutionService)
        assertNotNull(module.schedulingService)
    }

    @Test
    fun `test otel export only`() {
        configService.onlyUsingOtelExporters = true
        assertNotNull(module)
        assertNull(module.intakeService)
        assertNull(module.payloadCachingService)
        assertNull(module.payloadStorageService)
        assertNull(module.payloadResurrectionService)
        assertNull(module.requestExecutionService)
        assertNull(module.schedulingService)
    }
}

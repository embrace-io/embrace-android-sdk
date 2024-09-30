package io.embrace.android.embracesdk.internal.injection

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.embrace.android.embracesdk.fakes.FakeConfigModule
import io.embrace.android.embracesdk.fakes.FakeConfigService
import io.embrace.android.embracesdk.fakes.FakeEmbLogger
import io.embrace.android.embracesdk.fakes.injection.FakeEssentialServiceModule
import io.embrace.android.embracesdk.fakes.injection.FakeInitModule
import io.embrace.android.embracesdk.fakes.injection.FakeStorageModule
import io.embrace.android.embracesdk.fakes.injection.FakeWorkerThreadModule
import io.embrace.android.embracesdk.internal.comms.delivery.NoopDeliveryService
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DeliveryModuleImplTest {

    private lateinit var module: DeliveryModule
    private lateinit var configService: FakeConfigService

    @Before
    fun setUp() {
        configService = FakeConfigService()
        module = DeliveryModuleImpl(
            FakeConfigModule(configService),
            FakeInitModule(),
            FakeWorkerThreadModule(),
            CoreModuleImpl(ApplicationProvider.getApplicationContext(), FakeEmbLogger()),
            FakeStorageModule(),
            FakeEssentialServiceModule()
        )
    }

    @Test
    fun testModule() {
        assertNotNull(module)
        assertNotNull(module.deliveryService)
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
        assertTrue(module.deliveryService is NoopDeliveryService)
        assertNull(module.intakeService)
        assertNull(module.payloadCachingService)
        assertNull(module.payloadStorageService)
        assertNull(module.payloadResurrectionService)
        assertNull(module.requestExecutionService)
        assertNull(module.schedulingService)
    }
}

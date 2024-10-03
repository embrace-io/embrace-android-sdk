package io.embrace.android.embracesdk.internal.injection

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.embrace.android.embracesdk.fakes.FakeConfigModule
import io.embrace.android.embracesdk.fakes.FakeConfigService
import io.embrace.android.embracesdk.fakes.FakeDeliveryService
import io.embrace.android.embracesdk.fakes.FakeEmbLogger
import io.embrace.android.embracesdk.fakes.FakeRequestExecutionService
import io.embrace.android.embracesdk.fakes.behavior.FakeAutoDataCaptureBehavior
import io.embrace.android.embracesdk.fakes.injection.FakeEssentialServiceModule
import io.embrace.android.embracesdk.fakes.injection.FakeInitModule
import io.embrace.android.embracesdk.fakes.injection.FakeStorageModule
import io.embrace.android.embracesdk.fakes.injection.FakeWorkerThreadModule
import io.embrace.android.embracesdk.internal.delivery.caching.NoopPayloadCachingService
import io.embrace.android.embracesdk.internal.delivery.intake.NoopIntakeService
import io.embrace.android.embracesdk.internal.delivery.resurrection.NoopPayloadResurrectionService
import io.embrace.android.embracesdk.internal.delivery.scheduling.NoopSchedulingService
import io.embrace.android.embracesdk.internal.delivery.storage.NoopPayloadStorageService
import io.embrace.android.embracesdk.internal.session.orchestrator.NoopPayloadStore
import io.embrace.android.embracesdk.internal.session.orchestrator.V1PayloadStore
import io.embrace.android.embracesdk.internal.session.orchestrator.V2PayloadStore
import org.junit.Assert.assertNotNull
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
            FakeEssentialServiceModule(),
            ::FakeRequestExecutionService,
            ::FakeDeliveryService
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
        assertTrue(module.payloadStore is V1PayloadStore)
    }

    @Test
    fun `test otel export only`() {
        configService.onlyUsingOtelExporters = true
        assertNotNull(module)
        assertTrue(module.deliveryService is FakeDeliveryService)
        assertTrue(module.intakeService is NoopIntakeService)
        assertTrue(module.payloadCachingService is NoopPayloadCachingService)
        assertTrue(module.payloadStorageService is NoopPayloadStorageService)
        assertTrue(module.payloadResurrectionService is NoopPayloadResurrectionService)
        assertTrue(module.requestExecutionService is FakeRequestExecutionService)
        assertTrue(module.schedulingService is NoopSchedulingService)
        assertTrue(module.payloadStore is NoopPayloadStore)
    }

    @Test
    fun testV2Store() {
        configService.autoDataCaptureBehavior = FakeAutoDataCaptureBehavior(v2StorageEnabled = true)
        assertNotNull(module)
        assertTrue(module.payloadStore is V2PayloadStore)
    }
}

package io.embrace.android.embracesdk.internal.injection

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.embrace.android.embracesdk.fakes.FakeConfigModule
import io.embrace.android.embracesdk.fakes.FakeConfigService
import io.embrace.android.embracesdk.fakes.FakeDeliveryService
import io.embrace.android.embracesdk.fakes.FakeOpenTelemetryModule
import io.embrace.android.embracesdk.fakes.FakeRequestExecutionService
import io.embrace.android.embracesdk.fakes.behavior.FakeAutoDataCaptureBehavior
import io.embrace.android.embracesdk.fakes.injection.FakeAndroidServicesModule
import io.embrace.android.embracesdk.fakes.injection.FakeEssentialServiceModule
import io.embrace.android.embracesdk.fakes.injection.FakeInitModule
import io.embrace.android.embracesdk.fakes.injection.FakeStorageModule
import io.embrace.android.embracesdk.fakes.injection.FakeWorkerThreadModule
import io.embrace.android.embracesdk.internal.session.orchestrator.V2PayloadStore
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
        val initModule = FakeInitModule()
        module = DeliveryModuleImpl(
            FakeConfigModule(configService),
            initModule,
            FakeOpenTelemetryModule(),
            FakeWorkerThreadModule(),
            CoreModuleImpl(ApplicationProvider.getApplicationContext(), initModule),
            FakeStorageModule(),
            FakeEssentialServiceModule(),
            FakeAndroidServicesModule(),
            ::FakeRequestExecutionService,
            null,
            null,
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
        assertNotNull(module.cacheStorageService)
        assertNotNull(module.cachedLogEnvelopeStore)
        assertNotNull(module.requestExecutionService)
        assertNotNull(module.schedulingService)
        assertTrue(module.payloadStore is V2PayloadStore)
    }

    @Test
    fun `test otel export only`() {
        configService.onlyUsingOtelExporters = true
        assertNotNull(module)
        assertTrue(module.deliveryService is FakeDeliveryService)
        assertNull(module.intakeService)
        assertNull(module.payloadCachingService)
        assertNull(module.payloadStorageService)
        assertNull(module.cacheStorageService)
        assertNull(module.cachedLogEnvelopeStore)
        assertTrue(module.requestExecutionService is FakeRequestExecutionService)
        assertNull(module.schedulingService)
        assertNull(module.payloadStore)
    }

    @Test
    fun testV2Store() {
        configService.autoDataCaptureBehavior = FakeAutoDataCaptureBehavior(v2StorageEnabled = true)
        assertNotNull(module)
        assertTrue(module.payloadStore is V2PayloadStore)
    }
}

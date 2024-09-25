package io.embrace.android.embracesdk.internal.delivery

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.embrace.android.embracesdk.fakes.FakeConfigModule
import io.embrace.android.embracesdk.fakes.FakeConfigService
import io.embrace.android.embracesdk.fakes.FakeEmbLogger
import io.embrace.android.embracesdk.fakes.injection.FakeInitModule
import io.embrace.android.embracesdk.fakes.injection.FakeWorkerThreadModule
import io.embrace.android.embracesdk.internal.injection.CoreModuleImpl
import io.embrace.android.embracesdk.internal.injection.DeliveryModule2
import io.embrace.android.embracesdk.internal.injection.DeliveryModule2Impl
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
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
            CoreModuleImpl(ApplicationProvider.getApplicationContext(), FakeEmbLogger())
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

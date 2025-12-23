package io.embrace.android.embracesdk.internal.injection

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.embrace.android.embracesdk.fakes.FakeConfigService
import io.embrace.android.embracesdk.fakes.FakeOpenTelemetryModule
import io.embrace.android.embracesdk.fakes.FakeRequestExecutionService
import io.embrace.android.embracesdk.fakes.injection.FakeEssentialServiceModule
import io.embrace.android.embracesdk.fakes.injection.FakeInitModule
import io.embrace.android.embracesdk.fakes.injection.FakeWorkerThreadModule
import org.junit.Assert.assertNotNull
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
            configService,
            initModule,
            FakeOpenTelemetryModule(),
            FakeWorkerThreadModule(),
            CoreModuleImpl(ApplicationProvider.getApplicationContext(), initModule),
            FakeEssentialServiceModule(),
            ::FakeRequestExecutionService,
            null,
            null,
        )
    }

    @Test
    fun testModule() {
        assertNotNull(module)
        assertNotNull(module.intakeService)
        assertNotNull(module.payloadCachingService)
        assertNotNull(module.cacheStorageService)
        assertNotNull(module.cachedLogEnvelopeStore)
        assertNotNull(module.schedulingService)
        assertNotNull(module.payloadStore)
    }
}

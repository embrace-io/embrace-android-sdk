package io.embrace.android.embracesdk.internal.injection

import android.app.Application
import io.embrace.android.embracesdk.fakes.FakeConfigService
import io.embrace.android.embracesdk.fakes.FakeOpenTelemetryModule
import io.embrace.android.embracesdk.fakes.FakeRequestExecutionService
import io.embrace.android.embracesdk.fakes.OtelSdkMode
import io.embrace.android.embracesdk.fakes.injection.FakeEssentialServiceModule
import io.embrace.android.embracesdk.fakes.injection.FakeInitModule
import io.embrace.android.embracesdk.fakes.injection.FakeWorkerThreadPool
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test

class DeliveryModuleImplTest {

    private lateinit var module: DeliveryModule
    private lateinit var configService: FakeConfigService

    @Before
    fun setUp() {
        configService = FakeConfigService()
        val initModule = FakeInitModule(otelSdkMode = OtelSdkMode.COMPAT)
        module = DeliveryModuleImpl(
            configService,
            initModule,
            FakeOpenTelemetryModule(otelSdkMode = OtelSdkMode.REGULAR),
            FakeWorkerThreadPool(),
            CoreModuleImpl(Application(), initModule),
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

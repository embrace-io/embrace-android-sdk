package io.embrace.android.embracesdk.session

import io.embrace.android.embracesdk.fakes.FakeConfigService
import io.embrace.android.embracesdk.fakes.fakeEmbraceSessionProperties
import io.embrace.android.embracesdk.fakes.injection.FakeAndroidServicesModule
import io.embrace.android.embracesdk.fakes.injection.FakeCustomerLogModule
import io.embrace.android.embracesdk.fakes.injection.FakeDataCaptureServiceModule
import io.embrace.android.embracesdk.fakes.injection.FakeDataContainerModule
import io.embrace.android.embracesdk.fakes.injection.FakeDeliveryModule
import io.embrace.android.embracesdk.fakes.injection.FakeEssentialServiceModule
import io.embrace.android.embracesdk.fakes.injection.FakeInitModule
import io.embrace.android.embracesdk.fakes.injection.FakeNativeModule
import io.embrace.android.embracesdk.fakes.injection.FakeSdkObservabilityModule
import io.embrace.android.embracesdk.fakes.injection.FakeWorkerThreadModule
import io.embrace.android.embracesdk.injection.DataSourceModuleImpl
import io.embrace.android.embracesdk.injection.SessionModuleImpl
import io.embrace.android.embracesdk.worker.WorkerName
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

internal class SessionModuleImplTest {

    private val fakeInitModule = FakeInitModule()

    private val workerThreadModule = FakeWorkerThreadModule(fakeInitModule, WorkerName.BACKGROUND_REGISTRATION)

    private val configService = FakeConfigService()

    @Test
    fun testDefaultImplementations() {
        val essentialServiceModule = FakeEssentialServiceModule(configService = configService)
        val dataSourceModule = DataSourceModuleImpl(essentialServiceModule)
        val module = SessionModuleImpl(
            fakeInitModule,
            fakeInitModule.openTelemetryModule,
            FakeAndroidServicesModule(),
            essentialServiceModule,
            FakeNativeModule(),
            FakeDataContainerModule(),
            FakeDeliveryModule(),
            fakeEmbraceSessionProperties(),
            FakeDataCaptureServiceModule(),
            FakeCustomerLogModule(),
            FakeSdkObservabilityModule(),
            workerThreadModule,
            dataSourceModule
        )
        assertNotNull(module.payloadMessageCollator)
        assertNotNull(module.sessionPropertiesService)
        assertNotNull(module.payloadFactory)
        assertNotNull(module.sessionOrchestrator)
        assertNotNull(module.periodicSessionCacher)
        assertNotNull(module.periodicBackgroundActivityCacher)
        assertTrue(configService.listeners.contains(module.dataCaptureOrchestrator))
    }

    @Test
    fun testEnabledBehaviors() {
        val essentialServiceModule = createEnabledBehavior()
        val dataSourceModule = DataSourceModuleImpl(essentialServiceModule)

        val module = SessionModuleImpl(
            fakeInitModule,
            fakeInitModule.openTelemetryModule,
            FakeAndroidServicesModule(),
            essentialServiceModule,
            FakeNativeModule(),
            FakeDataContainerModule(),
            FakeDeliveryModule(),
            fakeEmbraceSessionProperties(),
            FakeDataCaptureServiceModule(),
            FakeCustomerLogModule(),
            FakeSdkObservabilityModule(),
            workerThreadModule,
            dataSourceModule
        )
        assertNotNull(module.payloadMessageCollator)
        assertNotNull(module.sessionPropertiesService)
        assertNotNull(module.payloadFactory)
        assertNotNull(module.sessionOrchestrator)
        assertNotNull(module.dataCaptureOrchestrator)
    }

    private fun createEnabledBehavior(): FakeEssentialServiceModule {
        return FakeEssentialServiceModule(
            configService = FakeConfigService(
                backgroundActivityCaptureEnabled = true
            )
        )
    }
}

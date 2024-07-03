package io.embrace.android.embracesdk.session

import io.embrace.android.embracesdk.fakes.FakeConfigService
import io.embrace.android.embracesdk.fakes.FakeOpenTelemetryModule
import io.embrace.android.embracesdk.fakes.FakePayloadModule
import io.embrace.android.embracesdk.fakes.injection.FakeAndroidServicesModule
import io.embrace.android.embracesdk.fakes.injection.FakeAnrModule
import io.embrace.android.embracesdk.fakes.injection.FakeCoreModule
import io.embrace.android.embracesdk.fakes.injection.FakeCustomerLogModule
import io.embrace.android.embracesdk.fakes.injection.FakeDataCaptureServiceModule
import io.embrace.android.embracesdk.fakes.injection.FakeDataContainerModule
import io.embrace.android.embracesdk.fakes.injection.FakeDeliveryModule
import io.embrace.android.embracesdk.fakes.injection.FakeEssentialServiceModule
import io.embrace.android.embracesdk.fakes.injection.FakeInitModule
import io.embrace.android.embracesdk.fakes.injection.FakeNativeModule
import io.embrace.android.embracesdk.fakes.injection.FakeSystemServiceModule
import io.embrace.android.embracesdk.fakes.injection.FakeWorkerThreadModule
import io.embrace.android.embracesdk.injection.DataSourceModuleImpl
import io.embrace.android.embracesdk.injection.SessionModuleImpl
import io.embrace.android.embracesdk.worker.WorkerName
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

internal class SessionModuleImplTest {

    private val initModule = FakeInitModule()
    private val coreModule = FakeCoreModule()
    private val otelModule = FakeOpenTelemetryModule()
    private val systemServiceModule = FakeSystemServiceModule()
    private val androidServicesModule = FakeAndroidServicesModule()
    private val configService = FakeConfigService()
    private val workerThreadModule = FakeWorkerThreadModule(
        fakeInitModule = initModule,
        name = WorkerName.BACKGROUND_REGISTRATION
    )

    @Test
    fun testDefaultImplementations() {
        val essentialServiceModule = FakeEssentialServiceModule(configService = configService)
        val dataSourceModule = DataSourceModuleImpl(
            initModule,
            coreModule,
            otelModule,
            essentialServiceModule,
            systemServiceModule,
            androidServicesModule,
            workerThreadModule,
            FakeAnrModule()
        )
        val module = SessionModuleImpl(
            initModule,
            initModule.openTelemetryModule,
            FakeAndroidServicesModule(),
            essentialServiceModule,
            FakeNativeModule(),
            FakeDeliveryModule(),
            workerThreadModule,
            dataSourceModule,
            FakePayloadModule(),
            FakeDataCaptureServiceModule(),
            FakeDataContainerModule(),
            FakeCustomerLogModule()
        )
        assertNotNull(module.payloadMessageCollatorImpl)
        assertNotNull(module.sessionPropertiesService)
        assertNotNull(module.payloadFactory)
        assertNotNull(module.sessionOrchestrator)
        assertNotNull(module.periodicSessionCacher)
        assertNotNull(module.periodicBackgroundActivityCacher)
        assertTrue(configService.listeners.single().javaClass.toString().contains("DataCaptureOrchestrator"))
    }

    @Test
    fun testEnabledBehaviors() {
        val essentialServiceModule = createEnabledBehavior()
        val dataSourceModule = DataSourceModuleImpl(
            initModule,
            coreModule,
            otelModule,
            essentialServiceModule,
            systemServiceModule,
            androidServicesModule,
            workerThreadModule,
            FakeAnrModule()
        )

        val module = SessionModuleImpl(
            initModule,
            initModule.openTelemetryModule,
            FakeAndroidServicesModule(),
            essentialServiceModule,
            FakeNativeModule(),
            FakeDeliveryModule(),
            workerThreadModule,
            dataSourceModule,
            FakePayloadModule(),
            FakeDataCaptureServiceModule(),
            FakeDataContainerModule(),
            FakeCustomerLogModule()
        )
        assertNotNull(module.payloadMessageCollatorImpl)
        assertNotNull(module.sessionPropertiesService)
        assertNotNull(module.payloadFactory)
        assertNotNull(module.sessionOrchestrator)
    }

    private fun createEnabledBehavior(): FakeEssentialServiceModule {
        return FakeEssentialServiceModule(
            configService = FakeConfigService(
                backgroundActivityCaptureEnabled = true
            )
        )
    }
}

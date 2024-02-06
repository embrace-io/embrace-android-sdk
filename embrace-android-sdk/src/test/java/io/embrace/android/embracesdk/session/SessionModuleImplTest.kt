package io.embrace.android.embracesdk.session

import io.embrace.android.embracesdk.FakeWorkerThreadModule
import io.embrace.android.embracesdk.concurrency.BlockableExecutorService
import io.embrace.android.embracesdk.concurrency.BlockingScheduledExecutorService
import io.embrace.android.embracesdk.fakes.FakeConfigService
import io.embrace.android.embracesdk.fakes.fakeEmbraceSessionProperties
import io.embrace.android.embracesdk.fakes.injection.FakeAndroidServicesModule
import io.embrace.android.embracesdk.fakes.injection.FakeCustomerLogModule
import io.embrace.android.embracesdk.fakes.injection.FakeDataCaptureServiceModule
import io.embrace.android.embracesdk.fakes.injection.FakeDataContainerModule
import io.embrace.android.embracesdk.fakes.injection.FakeDeliveryModule
import io.embrace.android.embracesdk.fakes.injection.FakeEssentialServiceModule
import io.embrace.android.embracesdk.fakes.injection.FakeNativeModule
import io.embrace.android.embracesdk.fakes.injection.FakeSdkObservabilityModule
import io.embrace.android.embracesdk.injection.InitModuleImpl
import io.embrace.android.embracesdk.injection.SessionModuleImpl
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

internal class SessionModuleImplTest {

    private val workerThreadModule =
        FakeWorkerThreadModule(
            scheduledExecutorProvider = ::BlockingScheduledExecutorService,
            executorProvider = ::BlockableExecutorService
        )

    private val configService = FakeConfigService()

    @Test
    fun testDefaultImplementations() {
        val module = SessionModuleImpl(
            InitModuleImpl(),
            FakeAndroidServicesModule(),
            FakeEssentialServiceModule(configService = configService),
            FakeNativeModule(),
            FakeDataContainerModule(),
            FakeDeliveryModule(),
            fakeEmbraceSessionProperties(),
            FakeDataCaptureServiceModule(),
            FakeCustomerLogModule(),
            FakeSdkObservabilityModule(),
            workerThreadModule
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
        val module = SessionModuleImpl(
            InitModuleImpl(),
            FakeAndroidServicesModule(),
            createEnabledBehavior(),
            FakeNativeModule(),
            FakeDataContainerModule(),
            FakeDeliveryModule(),
            fakeEmbraceSessionProperties(),
            FakeDataCaptureServiceModule(),
            FakeCustomerLogModule(),
            FakeSdkObservabilityModule(),
            workerThreadModule
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

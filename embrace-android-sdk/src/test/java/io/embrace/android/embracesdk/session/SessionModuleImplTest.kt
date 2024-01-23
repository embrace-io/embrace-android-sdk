package io.embrace.android.embracesdk.session

import io.embrace.android.embracesdk.FakeWorkerThreadModule
import io.embrace.android.embracesdk.concurrency.BlockableExecutorService
import io.embrace.android.embracesdk.concurrency.BlockingScheduledExecutorService
import io.embrace.android.embracesdk.fakes.FakeConfigService
import io.embrace.android.embracesdk.fakes.fakeEmbraceSessionProperties
import io.embrace.android.embracesdk.fakes.injection.FakeAndroidServicesModule
import io.embrace.android.embracesdk.fakes.injection.FakeCoreModule
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
import org.junit.Test

internal class SessionModuleImplTest {

    private val workerThreadModule =
        FakeWorkerThreadModule(
            scheduledExecutorProvider = ::BlockingScheduledExecutorService,
            executorProvider = ::BlockableExecutorService
        )

    @Test
    fun testDefaultImplementations() {
        val module = SessionModuleImpl(
            InitModuleImpl(),
            FakeCoreModule(),
            FakeAndroidServicesModule(),
            FakeEssentialServiceModule(),
            FakeNativeModule(),
            FakeDataContainerModule(),
            FakeDeliveryModule(),
            fakeEmbraceSessionProperties(),
            FakeDataCaptureServiceModule(),
            FakeCustomerLogModule(),
            FakeSdkObservabilityModule(),
            workerThreadModule
        )
        assertNotNull(module.sessionService)
        assertNotNull(module.payloadMessageCollator)
        assertNotNull(module.sessionPropertiesService)
        assertNotNull(module.backgroundActivityService)
        assertNotNull(module.sessionOrchestrator)
    }

    @Test
    fun testEnabledBehaviors() {
        val module = SessionModuleImpl(
            InitModuleImpl(),
            FakeCoreModule(),
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
        assertNotNull(module.sessionService)
        assertNotNull(module.payloadMessageCollator)
        assertNotNull(module.sessionPropertiesService)
        assertNotNull(module.backgroundActivityService)
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

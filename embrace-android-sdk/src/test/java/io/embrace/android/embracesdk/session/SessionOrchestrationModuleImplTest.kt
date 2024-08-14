package io.embrace.android.embracesdk.session

import io.embrace.android.embracesdk.fakes.FakeConfigService
import io.embrace.android.embracesdk.fakes.FakePayloadSourceModule
import io.embrace.android.embracesdk.fakes.injection.FakeAndroidServicesModule
import io.embrace.android.embracesdk.fakes.injection.FakeDataCaptureServiceModule
import io.embrace.android.embracesdk.fakes.injection.FakeDeliveryModule
import io.embrace.android.embracesdk.fakes.injection.FakeEssentialServiceModule
import io.embrace.android.embracesdk.fakes.injection.FakeInitModule
import io.embrace.android.embracesdk.fakes.injection.FakeLogModule
import io.embrace.android.embracesdk.fakes.injection.FakeMomentsModule
import io.embrace.android.embracesdk.fakes.injection.FakeWorkerThreadModule
import io.embrace.android.embracesdk.internal.injection.SessionOrchestrationModuleImpl
import io.embrace.android.embracesdk.internal.injection.createDataSourceModule
import io.embrace.android.embracesdk.internal.worker.WorkerName
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

internal class SessionOrchestrationModuleImplTest {

    private val initModule = FakeInitModule()
    private val configService = FakeConfigService()
    private val workerThreadModule = FakeWorkerThreadModule(
        fakeInitModule = initModule,
        name = WorkerName.BACKGROUND_REGISTRATION
    )

    @Test
    fun testDefaultImplementations() {
        val essentialServiceModule = FakeEssentialServiceModule(configService = configService)
        val dataSourceModule = createDataSourceModule(
            initModule,
            configService,
            workerThreadModule
        )
        val module = SessionOrchestrationModuleImpl(
            initModule,
            initModule.openTelemetryModule,
            FakeAndroidServicesModule(),
            essentialServiceModule,
            FakeDeliveryModule(),
            workerThreadModule,
            dataSourceModule,
            FakePayloadSourceModule(),
            FakeDataCaptureServiceModule(),
            FakeMomentsModule(),
            FakeLogModule()
        )
        assertNotNull(module.payloadMessageCollatorImpl)
        assertNotNull(module.payloadFactory)
        assertNotNull(module.sessionOrchestrator)
        assertNotNull(module.periodicSessionCacher)
        assertNotNull(module.periodicBackgroundActivityCacher)
        assertTrue(
            configService.listeners.single().javaClass.toString()
                .contains("DataCaptureOrchestrator")
        )
    }

    @Test
    fun testEnabledBehaviors() {
        val essentialServiceModule = createEnabledBehavior()
        val dataSourceModule = createDataSourceModule(
            initModule,
            configService,
            workerThreadModule
        )

        val module = SessionOrchestrationModuleImpl(
            initModule,
            initModule.openTelemetryModule,
            FakeAndroidServicesModule(),
            essentialServiceModule,
            FakeDeliveryModule(),
            workerThreadModule,
            dataSourceModule,
            FakePayloadSourceModule(),
            FakeDataCaptureServiceModule(),
            FakeMomentsModule(),
            FakeLogModule()
        )
        assertNotNull(module.payloadMessageCollatorImpl)
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

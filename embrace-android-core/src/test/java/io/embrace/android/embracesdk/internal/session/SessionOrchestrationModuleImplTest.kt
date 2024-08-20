package io.embrace.android.embracesdk.internal.session

import io.embrace.android.embracesdk.fakes.FakeConfigModule
import io.embrace.android.embracesdk.fakes.FakeConfigService
import io.embrace.android.embracesdk.fakes.injection.FakeAndroidServicesModule
import io.embrace.android.embracesdk.fakes.injection.FakeDeliveryModule
import io.embrace.android.embracesdk.fakes.injection.FakeEssentialServiceModule
import io.embrace.android.embracesdk.fakes.injection.FakeInitModule
import io.embrace.android.embracesdk.fakes.injection.FakeLogModule
import io.embrace.android.embracesdk.fakes.injection.FakeMomentsModule
import io.embrace.android.embracesdk.fakes.injection.FakePayloadSourceModule
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
        val dataSourceModule = createDataSourceModule(
            initModule,
            configService,
            workerThreadModule
        )
        val module = SessionOrchestrationModuleImpl(
            initModule,
            initModule.openTelemetryModule,
            FakeAndroidServicesModule(),
            FakeEssentialServiceModule(),
            FakeConfigModule(configService = configService),
            FakeDeliveryModule(),
            workerThreadModule,
            dataSourceModule,
            FakePayloadSourceModule(),
            { 0L },
            FakeMomentsModule(),
            FakeLogModule()
        )
        assertNotNull(module.payloadMessageCollator)
        assertNotNull(module.payloadFactory)
        assertNotNull(module.sessionOrchestrator)
        assertTrue(
            configService.listeners.single().javaClass.toString()
                .contains("DataCaptureOrchestrator")
        )
    }

    @Test
    fun testEnabledBehaviors() {
        val configModule = createEnabledBehavior()
        val dataSourceModule = createDataSourceModule(
            initModule,
            configService,
            workerThreadModule
        )

        val module = SessionOrchestrationModuleImpl(
            initModule,
            initModule.openTelemetryModule,
            FakeAndroidServicesModule(),
            FakeEssentialServiceModule(),
            configModule,
            FakeDeliveryModule(),
            workerThreadModule,
            dataSourceModule,
            FakePayloadSourceModule(),
            { 0L },
            FakeMomentsModule(),
            FakeLogModule()
        )
        assertNotNull(module.payloadMessageCollator)
        assertNotNull(module.payloadFactory)
        assertNotNull(module.sessionOrchestrator)
    }

    private fun createEnabledBehavior(): FakeConfigModule {
        return FakeConfigModule(
            configService = FakeConfigService(
                backgroundActivityCaptureEnabled = true
            )
        )
    }
}

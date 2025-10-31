package io.embrace.android.embracesdk.internal.session

import io.embrace.android.embracesdk.fakes.FakeConfigModule
import io.embrace.android.embracesdk.fakes.FakeConfigService
import io.embrace.android.embracesdk.fakes.FakeStartupService
import io.embrace.android.embracesdk.fakes.createBackgroundActivityBehavior
import io.embrace.android.embracesdk.fakes.injection.FakeAndroidServicesModule
import io.embrace.android.embracesdk.fakes.injection.FakeDeliveryModule
import io.embrace.android.embracesdk.fakes.injection.FakeEssentialServiceModule
import io.embrace.android.embracesdk.fakes.injection.FakeInitModule
import io.embrace.android.embracesdk.fakes.injection.FakeLogModule
import io.embrace.android.embracesdk.fakes.injection.FakePayloadSourceModule
import io.embrace.android.embracesdk.fakes.injection.FakeWorkerThreadModule
import io.embrace.android.embracesdk.internal.config.remote.BackgroundActivityRemoteConfig
import io.embrace.android.embracesdk.internal.config.remote.RemoteConfig
import io.embrace.android.embracesdk.internal.injection.CoreModuleImpl
import io.embrace.android.embracesdk.internal.injection.SessionOrchestrationModuleImpl
import io.embrace.android.embracesdk.internal.injection.createInstrumentationModule
import io.embrace.android.embracesdk.internal.worker.Worker
import io.mockk.mockk
import org.junit.Assert.assertNotNull
import org.junit.Test

internal class SessionOrchestrationModuleImplTest {

    private val initModule = FakeInitModule()
    private val configService = FakeConfigService()
    private val workerThreadModule = FakeWorkerThreadModule(
        fakeInitModule = initModule,
        testWorker = Worker.Background.NonIoRegWorker
    )

    @Test
    fun testDefaultImplementations() {
        val dataSourceModule = createInstrumentationModule(
            initModule,
            workerThreadModule,
            FakeConfigModule(),
            FakeEssentialServiceModule(),
            FakeAndroidServicesModule(),
            CoreModuleImpl(mockk(), initModule)
        )
        val module = SessionOrchestrationModuleImpl(
            initModule,
            initModule.openTelemetryModule,
            FakeAndroidServicesModule(),
            FakeEssentialServiceModule(),
            FakeConfigModule(configService = configService),
            FakeDeliveryModule(),
            dataSourceModule,
            FakePayloadSourceModule(),
            FakeStartupService(),
            FakeLogModule()
        )
        assertNotNull(module.payloadMessageCollator)
        assertNotNull(module.payloadFactory)
        assertNotNull(module.sessionOrchestrator)
    }

    @Test
    fun testEnabledBehaviors() {
        val configModule = createEnabledBehavior()
        val dataSourceModule = createInstrumentationModule(
            initModule,
            workerThreadModule,
            FakeConfigModule(),
            FakeEssentialServiceModule(),
            FakeAndroidServicesModule(),
            CoreModuleImpl(mockk(), initModule)
        )

        val module = SessionOrchestrationModuleImpl(
            initModule,
            initModule.openTelemetryModule,
            FakeAndroidServicesModule(),
            FakeEssentialServiceModule(),
            configModule,
            FakeDeliveryModule(),
            dataSourceModule,
            FakePayloadSourceModule(),
            FakeStartupService(),
            FakeLogModule()
        )
        assertNotNull(module.payloadMessageCollator)
        assertNotNull(module.payloadFactory)
        assertNotNull(module.sessionOrchestrator)
    }

    private fun createEnabledBehavior(): FakeConfigModule {
        return FakeConfigModule(
            configService = FakeConfigService(
                backgroundActivityBehavior = createBackgroundActivityBehavior(
                    remoteCfg = RemoteConfig(backgroundActivityConfig = BackgroundActivityRemoteConfig(threshold = 100f))
                ),
            )
        )
    }
}

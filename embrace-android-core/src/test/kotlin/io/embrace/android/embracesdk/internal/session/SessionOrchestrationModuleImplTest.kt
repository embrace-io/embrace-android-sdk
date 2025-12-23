package io.embrace.android.embracesdk.internal.session

import io.embrace.android.embracesdk.fakes.FakeConfigModule
import io.embrace.android.embracesdk.fakes.FakeConfigService
import io.embrace.android.embracesdk.fakes.FakeDeliveryModule
import io.embrace.android.embracesdk.fakes.FakeOpenTelemetryModule
import io.embrace.android.embracesdk.fakes.FakeStorageService
import io.embrace.android.embracesdk.fakes.createBackgroundActivityBehavior
import io.embrace.android.embracesdk.fakes.injection.FakeCoreModule
import io.embrace.android.embracesdk.fakes.injection.FakeEssentialServiceModule
import io.embrace.android.embracesdk.fakes.injection.FakeInitModule
import io.embrace.android.embracesdk.fakes.injection.FakeLogModule
import io.embrace.android.embracesdk.fakes.injection.FakePayloadSourceModule
import io.embrace.android.embracesdk.fakes.injection.FakeWorkerThreadModule
import io.embrace.android.embracesdk.internal.config.remote.BackgroundActivityRemoteConfig
import io.embrace.android.embracesdk.internal.config.remote.RemoteConfig
import io.embrace.android.embracesdk.internal.injection.InstrumentationModuleImpl
import io.embrace.android.embracesdk.internal.injection.SessionOrchestrationModuleImpl
import io.embrace.android.embracesdk.internal.worker.Worker
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
        val coreModule = FakeCoreModule()
        val dataSourceModule = InstrumentationModuleImpl(
            initModule,
            FakeOpenTelemetryModule(),
            workerThreadModule,
            FakeConfigModule(),
            FakeEssentialServiceModule(),
            coreModule,
            FakeStorageService()
        )
        val module = SessionOrchestrationModuleImpl(
            initModule,
            initModule.openTelemetryModule,
            coreModule,
            FakeEssentialServiceModule(),
            FakeConfigModule(configService = configService),
            FakeDeliveryModule(),
            dataSourceModule,
            FakePayloadSourceModule(),
            { 0 },
            FakeLogModule()
        )
        assertNotNull(module.sessionOrchestrator)
    }

    @Test
    fun testEnabledBehaviors() {
        val configModule = createEnabledBehavior()
        val coreModule = FakeCoreModule()
        val dataSourceModule = InstrumentationModuleImpl(
            initModule,
            FakeOpenTelemetryModule(),
            workerThreadModule,
            FakeConfigModule(),
            FakeEssentialServiceModule(),
            coreModule,
            FakeStorageService()
        )

        val module = SessionOrchestrationModuleImpl(
            initModule,
            initModule.openTelemetryModule,
            coreModule,
            FakeEssentialServiceModule(),
            configModule,
            FakeDeliveryModule(),
            dataSourceModule,
            FakePayloadSourceModule(),
            { 0 },
            FakeLogModule()
        )
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

package io.embrace.android.embracesdk.internal.injection

import android.app.Application
import android.os.Looper
import io.embrace.android.embracesdk.fakes.FakeConfigModule
import io.embrace.android.embracesdk.fakes.FakeConfigService
import io.embrace.android.embracesdk.fakes.FakeEmbLogger
import io.embrace.android.embracesdk.fakes.FakeOpenTelemetryModule
import io.embrace.android.embracesdk.fakes.behavior.FakeAutoDataCaptureBehavior
import io.embrace.android.embracesdk.fakes.injection.FakeAndroidServicesModule
import io.embrace.android.embracesdk.fakes.injection.FakeInitModule
import io.embrace.android.embracesdk.fakes.injection.FakeStorageModule
import io.embrace.android.embracesdk.fakes.injection.FakeSystemServiceModule
import io.embrace.android.embracesdk.fakes.injection.FakeWorkerThreadModule
import io.embrace.android.embracesdk.internal.comms.delivery.EmbracePendingApiCallsSender
import io.embrace.android.embracesdk.internal.comms.delivery.NoopPendingApiCallSender
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

internal class EssentialServiceModuleImplTest {

    @Before
    fun setup() {
        mockkStatic(Looper::class)
        every { Looper.getMainLooper() } returns mockk(relaxed = true)
    }

    @Test
    fun `check default module implementations`() {
        val module = createModule()
        assertNotNull(module.processStateService)
        assertNotNull(module.urlBuilder)
        assertNotNull(module.apiClient)
        assertNotNull(module.apiService)
        assertNotNull(module.activityLifecycleTracker)
        assertNotNull(module.sessionIdTracker)
        assertNotNull(module.sessionPropertiesService)
        assertNotNull(module.userService)
        assertNotNull(module.networkConnectivityService)
        assertNotNull(module.logWriter)
        assertTrue(module.pendingApiCallsSender is NoopPendingApiCallSender)
    }

    @Test
    fun `check modules when v2 delivery layer is off`() {
        val module = createModule(
            configModule = FakeConfigModule(
                configService = FakeConfigService(
                    autoDataCaptureBehavior = FakeAutoDataCaptureBehavior(v2StorageEnabled = false)
                )
            )
        )
        assertTrue(module.pendingApiCallsSender is EmbracePendingApiCallsSender)
    }

    private fun createModule(
        configModule: ConfigModule = FakeConfigModule(),
    ): EssentialServiceModule {
        val fakeApplication: Application = mockk(relaxed = true)
        return EssentialServiceModuleImpl(
            initModule = FakeInitModule(),
            openTelemetryModule = FakeOpenTelemetryModule(),
            coreModule = createCoreModule(fakeApplication, FakeEmbLogger()),
            workerThreadModule = FakeWorkerThreadModule(),
            systemServiceModule = FakeSystemServiceModule(),
            androidServicesModule = FakeAndroidServicesModule(),
            storageModule = FakeStorageModule(),
            configModule = configModule,
            networkConnectivityServiceProvider = { null }
        )
    }
}

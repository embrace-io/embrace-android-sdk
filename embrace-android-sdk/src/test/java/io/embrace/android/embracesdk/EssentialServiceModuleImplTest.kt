package io.embrace.android.embracesdk

import android.os.Looper
import io.embrace.android.embracesdk.capture.connectivity.EmbraceNetworkConnectivityService
import io.embrace.android.embracesdk.capture.cpu.EmbraceCpuInfoDelegate
import io.embrace.android.embracesdk.capture.metadata.EmbraceMetadataService
import io.embrace.android.embracesdk.capture.orientation.NoOpOrientationService
import io.embrace.android.embracesdk.capture.user.EmbraceUserService
import io.embrace.android.embracesdk.comms.delivery.EmbracePendingApiCallsSender
import io.embrace.android.embracesdk.config.EmbraceConfigService
import io.embrace.android.embracesdk.fakes.FakeConfigService
import io.embrace.android.embracesdk.fakes.injection.FakeAndroidServicesModule
import io.embrace.android.embracesdk.fakes.injection.FakeCoreModule
import io.embrace.android.embracesdk.fakes.injection.FakeStorageModule
import io.embrace.android.embracesdk.fakes.injection.FakeSystemServiceModule
import io.embrace.android.embracesdk.gating.EmbraceGatingService
import io.embrace.android.embracesdk.injection.EssentialServiceModuleImpl
import io.embrace.android.embracesdk.injection.InitModuleImpl
import io.embrace.android.embracesdk.internal.DeviceArchitectureImpl
import io.embrace.android.embracesdk.session.EmbraceMemoryCleanerService
import io.embrace.android.embracesdk.session.lifecycle.EmbraceProcessStateService
import io.embrace.android.embracesdk.worker.WorkerThreadModuleImpl
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

internal class EssentialServiceModuleImplTest {

    @Test
    fun testDefaultImplementations() {
        mockkStatic(Looper::class)
        every { Looper.getMainLooper() } returns mockk(relaxed = true)

        val coreModule = FakeCoreModule()
        val initModule = InitModuleImpl()
        val module = EssentialServiceModuleImpl(
            initModule = initModule,
            coreModule = coreModule,
            workerThreadModule = WorkerThreadModuleImpl(initModule),
            systemServiceModule = FakeSystemServiceModule(),
            androidServicesModule = FakeAndroidServicesModule(),
            storageModule = FakeStorageModule(),
            customAppId = "abcde",
            enableIntegrationTesting = false,
            configServiceProvider = { null }
        )

        assertTrue(module.memoryCleanerService is EmbraceMemoryCleanerService)
        assertTrue(module.orientationService is NoOpOrientationService)
        assertTrue(module.processStateService is EmbraceProcessStateService)
        assertTrue(module.metadataService is EmbraceMetadataService)
        assertNotNull(module.urlBuilder)
        assertNotNull(module.apiClient)
        assertNotNull(module.apiService)
        assertNotNull(module.activityLifecycleTracker)
        assertNotNull(module.sharedObjectLoader)
        assertNotNull(module.sessionIdTracker)
        assertTrue(module.userService is EmbraceUserService)
        assertTrue(module.configService is EmbraceConfigService)
        assertTrue(module.gatingService is EmbraceGatingService)
        assertTrue(module.cpuInfoDelegate is EmbraceCpuInfoDelegate)
        assertTrue(module.networkConnectivityService is EmbraceNetworkConnectivityService)
        assertTrue(module.deviceArchitecture is DeviceArchitectureImpl)
        assertTrue(module.pendingApiCallsSender is EmbracePendingApiCallsSender)
    }

    @Test
    fun testConfigServiceProvider() {
        val fakeConfigService = FakeConfigService()
        val module = EssentialServiceModuleImpl(
            initModule = InitModuleImpl(),
            coreModule = FakeCoreModule(),
            workerThreadModule = FakeWorkerThreadModule(),
            systemServiceModule = FakeSystemServiceModule(),
            androidServicesModule = FakeAndroidServicesModule(),
            storageModule = FakeStorageModule(),
            customAppId = null,
            enableIntegrationTesting = false,
            configServiceProvider = { fakeConfigService }
        )

        assertSame(fakeConfigService, module.configService)
    }
}

package io.embrace.android.embracesdk

import android.os.Looper
import io.embrace.android.embracesdk.capture.metadata.EmbraceMetadataService
import io.embrace.android.embracesdk.capture.orientation.NoOpOrientationService
import io.embrace.android.embracesdk.config.EmbraceConfigService
import io.embrace.android.embracesdk.fakes.FakeConfigService
import io.embrace.android.embracesdk.fakes.injection.FakeAndroidServicesModule
import io.embrace.android.embracesdk.fakes.injection.FakeCoreModule
import io.embrace.android.embracesdk.fakes.injection.FakeSystemServiceModule
import io.embrace.android.embracesdk.gating.EmbraceGatingService
import io.embrace.android.embracesdk.injection.EssentialServiceModuleImpl
import io.embrace.android.embracesdk.injection.InitModuleImpl
import io.embrace.android.embracesdk.internal.BuildInfo
import io.embrace.android.embracesdk.session.EmbraceActivityService
import io.embrace.android.embracesdk.session.EmbraceMemoryCleanerService
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
        val module = EssentialServiceModuleImpl(
            initModule = InitModuleImpl(),
            coreModule = coreModule,
            workerThreadModule = WorkerThreadModuleImpl(),
            systemServiceModule = FakeSystemServiceModule(),
            androidServicesModule = FakeAndroidServicesModule(),
            buildInfo = BuildInfo("", "", ""),
            customAppId = "abcde",
            enableIntegrationTesting = false,
            configStopAction = {},
            configServiceProvider = { null }
        )

        assertTrue(module.memoryCleanerService is EmbraceMemoryCleanerService)
        assertTrue(module.orientationService is NoOpOrientationService)
        assertTrue(module.activityService is EmbraceActivityService)
        assertTrue(module.metadataService is EmbraceMetadataService)
        assertNotNull(module.urlBuilder)
        assertNotNull(module.cache)
        assertNotNull(module.apiClient)
        assertNotNull(module.apiService)
        assertTrue(module.configService is EmbraceConfigService)
        assertTrue(module.gatingService is EmbraceGatingService)
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
            buildInfo = BuildInfo("", "", ""),
            customAppId = null,
            enableIntegrationTesting = false,
            configStopAction = {},
            configServiceProvider = { fakeConfigService }
        )

        assertSame(fakeConfigService, module.configService)
    }
}

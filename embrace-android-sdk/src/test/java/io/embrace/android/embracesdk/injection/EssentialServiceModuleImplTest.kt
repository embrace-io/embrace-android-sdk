package io.embrace.android.embracesdk.injection

import android.os.Looper
import io.embrace.android.embracesdk.fakes.FakeConfigModule
import io.embrace.android.embracesdk.fakes.FakeOpenTelemetryModule
import io.embrace.android.embracesdk.fakes.injection.FakeAndroidServicesModule
import io.embrace.android.embracesdk.fakes.injection.FakeCoreModule
import io.embrace.android.embracesdk.fakes.injection.FakeInitModule
import io.embrace.android.embracesdk.fakes.injection.FakeStorageModule
import io.embrace.android.embracesdk.fakes.injection.FakeSystemServiceModule
import io.embrace.android.embracesdk.fakes.injection.FakeWorkerThreadModule
import io.embrace.android.embracesdk.internal.injection.EssentialServiceModuleImpl
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import org.junit.Assert.assertNotNull
import org.junit.Test

internal class EssentialServiceModuleImplTest {

    @Test
    fun testDefaultImplementations() {
        mockkStatic(Looper::class)
        every { Looper.getMainLooper() } returns mockk(relaxed = true)

        val coreModule = FakeCoreModule()
        val initModule = FakeInitModule()
        val module = EssentialServiceModuleImpl(
            initModule = initModule,
            openTelemetryModule = FakeOpenTelemetryModule(),
            coreModule = coreModule,
            workerThreadModule = FakeWorkerThreadModule(),
            systemServiceModule = FakeSystemServiceModule(),
            androidServicesModule = FakeAndroidServicesModule(),
            storageModule = FakeStorageModule(),
            configModule = FakeConfigModule()
        )

        assertNotNull(module.processStateService)
        assertNotNull(module.urlBuilder)
        assertNotNull(module.apiClient)
        assertNotNull(module.apiService)
        assertNotNull(module.activityLifecycleTracker)
        assertNotNull(module.sessionIdTracker)
        assertNotNull(module.sessionPropertiesService)
        assertNotNull(module.userService)
        assertNotNull(module.networkConnectivityService)
        assertNotNull(module.pendingApiCallsSender)
        assertNotNull(module.logWriter)
    }
}

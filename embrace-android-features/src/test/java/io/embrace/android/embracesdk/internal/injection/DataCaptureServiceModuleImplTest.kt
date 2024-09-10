package io.embrace.android.embracesdk.internal.injection

import io.embrace.android.embracesdk.fakes.FakeConfigService
import io.embrace.android.embracesdk.fakes.FakeFeatureModule
import io.embrace.android.embracesdk.fakes.FakeVersionChecker
import io.embrace.android.embracesdk.fakes.createAnrBehavior
import io.embrace.android.embracesdk.fakes.createSdkModeBehavior
import io.embrace.android.embracesdk.fakes.injection.FakeInitModule
import io.embrace.android.embracesdk.fakes.injection.FakeWorkerThreadModule
import io.embrace.android.embracesdk.internal.config.remote.AnrRemoteConfig
import org.junit.Assert.assertNotNull
import org.junit.Test

internal class DataCaptureServiceModuleImplTest {

    private val initModule = FakeInitModule()
    private val openTelemetryModule = initModule.openTelemetryModule

    @Test
    fun testDefaultImplementations() {
        val module = DataCaptureServiceModuleImpl(
            initModule,
            openTelemetryModule,
            createEnabledBehavior(),
            FakeWorkerThreadModule(),
            FakeVersionChecker(false),
            FakeFeatureModule()
        )

        assertNotNull(module.webviewService)
        assertNotNull(module.activityBreadcrumbTracker)
        assertNotNull(module.appStartupDataCollector)
        assertNotNull(module.pushNotificationService)
        assertNotNull(module.startupService)
    }

    private fun createEnabledBehavior(): FakeConfigService {
        return FakeConfigService(
            anrBehavior = createAnrBehavior { AnrRemoteConfig(pctStrictModeListenerEnabled = 100f) },
            sdkModeBehavior = createSdkModeBehavior(
                isDebug = true
            )
        )
    }
}

package io.embrace.android.embracesdk.internal.injection

import io.embrace.android.embracesdk.fakes.FakeConfigService
import io.embrace.android.embracesdk.fakes.FakeFeatureModule
import io.embrace.android.embracesdk.fakes.FakeVersionChecker
import io.embrace.android.embracesdk.fakes.injection.FakeInitModule
import io.embrace.android.embracesdk.fakes.injection.FakeWorkerThreadModule
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
            FakeConfigService(),
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
}

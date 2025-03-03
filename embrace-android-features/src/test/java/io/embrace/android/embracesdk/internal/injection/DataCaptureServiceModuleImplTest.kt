package io.embrace.android.embracesdk.internal.injection

import io.embrace.android.embracesdk.fakes.FakeConfigService
import io.embrace.android.embracesdk.fakes.FakeFeatureModule
import io.embrace.android.embracesdk.fakes.FakeVersionChecker
import io.embrace.android.embracesdk.fakes.behavior.FakeAutoDataCaptureBehavior
import io.embrace.android.embracesdk.fakes.injection.FakeInitModule
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
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
            FakeVersionChecker(false),
            FakeFeatureModule()
        )

        assertNotNull(module.webviewService)
        assertNotNull(module.activityBreadcrumbTracker)
        assertNotNull(module.appStartupDataCollector)
        assertNotNull(module.pushNotificationService)
        assertNotNull(module.startupService)
        assertNull(module.activityLoadEventEmitter)
        assertNull(module.uiLoadDataListener)
    }

    @Test
    fun `disable ui load performance capture`() {
        val module = DataCaptureServiceModuleImpl(
            initModule,
            openTelemetryModule,
            FakeConfigService(
                autoDataCaptureBehavior = FakeAutoDataCaptureBehavior(uiLoadTracingEnabled = false)
            ),
            FakeVersionChecker(false),
            FakeFeatureModule()
        )

        assertNull(module.uiLoadDataListener)
        assertNull(module.activityLoadEventEmitter)
    }

    @Test
    fun `enable only selected ui load performance capture`() {
        val module = DataCaptureServiceModuleImpl(
            initModule,
            openTelemetryModule,
            FakeConfigService(
                autoDataCaptureBehavior = FakeAutoDataCaptureBehavior(uiLoadTracingTraceAll = false)
            ),
            FakeVersionChecker(false),
            FakeFeatureModule()
        )

        assertNotNull(module.uiLoadDataListener)
        assertNotNull(module.activityLoadEventEmitter)
    }
}

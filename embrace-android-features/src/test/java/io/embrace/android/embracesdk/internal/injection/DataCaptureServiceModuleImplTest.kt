package io.embrace.android.embracesdk.internal.injection

import io.embrace.android.embracesdk.fakes.FakeConfigService
import io.embrace.android.embracesdk.fakes.FakeInstrumentationModule
import io.embrace.android.embracesdk.fakes.FakeVersionChecker
import io.embrace.android.embracesdk.fakes.behavior.FakeAutoDataCaptureBehavior
import io.embrace.android.embracesdk.fakes.injection.FakeInitModule
import io.mockk.mockk
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

internal class DataCaptureServiceModuleImplTest {

    private val initModule = FakeInitModule()
    private val instrumentationModule = FakeInstrumentationModule(mockk())

    @Test
    fun testDefaultImplementations() {
        val module = DataCaptureServiceModuleImpl(
            initModule,
            instrumentationModule,
            FakeConfigService(),
            FakeVersionChecker(false)
        )

        assertNotNull(module.appStartupDataCollector)
        assertNotNull(module.startupService)
        assertNotNull(module.activityLoadEventEmitter)
        assertNotNull(module.uiLoadDataListener)
    }

    @Test
    fun `disable ui load performance capture`() {
        val module = DataCaptureServiceModuleImpl(
            initModule,
            instrumentationModule,
            FakeConfigService(
                autoDataCaptureBehavior = FakeAutoDataCaptureBehavior(uiLoadTracingEnabled = false)
            ),
            FakeVersionChecker(false)
        )

        assertNull(module.uiLoadDataListener)
        assertNull(module.activityLoadEventEmitter)
    }

    @Test
    fun `enable only selected ui load performance capture`() {
        val module = DataCaptureServiceModuleImpl(
            initModule,
            instrumentationModule,
            FakeConfigService(
                autoDataCaptureBehavior = FakeAutoDataCaptureBehavior(uiLoadTracingTraceAll = false)
            ),
            FakeVersionChecker(false)
        )

        assertNotNull(module.uiLoadDataListener)
        assertNotNull(module.activityLoadEventEmitter)
    }
}

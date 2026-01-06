package io.embrace.android.embracesdk.internal.instrumentation.startup

import io.embrace.android.embracesdk.fakes.FakeClock
import io.embrace.android.embracesdk.fakes.FakeConfigService
import io.embrace.android.embracesdk.fakes.FakeInternalLogger
import io.embrace.android.embracesdk.fakes.FakeTelemetryDestination
import io.embrace.android.embracesdk.fakes.behavior.FakeAutoDataCaptureBehavior
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

internal class DataCaptureServiceModuleImplTest {

    @Test
    fun testDefaultImplementations() {
        val module = DataCaptureServiceModuleImpl(
            FakeClock(),
            FakeInternalLogger(),
            FakeTelemetryDestination(),
            FakeConfigService(),
        )

        assertNotNull(module.appStartupDataCollector)
        assertNotNull(module.startupService)
        assertNotNull(module.activityLoadEventEmitter)
        assertNotNull(module.uiLoadDataListener)
    }

    @Test
    fun `disable ui load performance capture`() {
        val module = DataCaptureServiceModuleImpl(
            FakeClock(),
            FakeInternalLogger(),
            FakeTelemetryDestination(),
            FakeConfigService(
                autoDataCaptureBehavior = FakeAutoDataCaptureBehavior(uiLoadTracingEnabled = false)
            ),
        )

        assertNull(module.uiLoadDataListener)
        assertNull(module.activityLoadEventEmitter)
    }

    @Test
    fun `enable only selected ui load performance capture`() {
        val module = DataCaptureServiceModuleImpl(
            FakeClock(),
            FakeInternalLogger(),
            FakeTelemetryDestination(),
            FakeConfigService(
                autoDataCaptureBehavior = FakeAutoDataCaptureBehavior(uiLoadTracingTraceAll = false)
            ),
        )

        assertNotNull(module.uiLoadDataListener)
        assertNotNull(module.activityLoadEventEmitter)
    }
}

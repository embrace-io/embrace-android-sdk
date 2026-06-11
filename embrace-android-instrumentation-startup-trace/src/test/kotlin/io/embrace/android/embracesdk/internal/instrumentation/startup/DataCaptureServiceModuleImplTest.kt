package io.embrace.android.embracesdk.internal.instrumentation.startup

import io.embrace.android.embracesdk.fakes.FakeClock
import io.embrace.android.embracesdk.fakes.FakeConfigService
import io.embrace.android.embracesdk.fakes.FakeInternalLogger
import io.embrace.android.embracesdk.fakes.FakeTelemetryDestination
import io.embrace.android.embracesdk.fakes.behavior.FakeAutoDataCaptureBehavior
import io.embrace.android.embracesdk.internal.arch.startup.StartupClassifierImpl
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

internal class DataCaptureServiceModuleImplTest {

    @Test
    fun testDefaultImplementations() {
        val module = DataCaptureServiceModuleImpl(
            clock = FakeClock(),
            logger = FakeInternalLogger(),
            destination = FakeTelemetryDestination(),
            configService = FakeConfigService(),
            startupClassifier = StartupClassifierImpl(),
        )

        assertNotNull(module.appStartupDataCollector)
        assertNotNull(module.startupService)
        assertNotNull(module.activityLoadEventEmitter)
        assertNotNull(module.uiLoadDataListener)
    }

    @Test
    fun `disable ui load performance capture`() {
        val module = DataCaptureServiceModuleImpl(
            clock = FakeClock(),
            logger = FakeInternalLogger(),
            destination = FakeTelemetryDestination(),
            configService = FakeConfigService(
                autoDataCaptureBehavior = FakeAutoDataCaptureBehavior(uiLoadTracingEnabled = false)
            ),
            startupClassifier = StartupClassifierImpl(),
        )

        assertNull(module.uiLoadDataListener)
        assertNull(module.activityLoadEventEmitter)
    }

    @Test
    fun `enable only selected ui load performance capture`() {
        val module = DataCaptureServiceModuleImpl(
            clock = FakeClock(),
            logger = FakeInternalLogger(),
            destination = FakeTelemetryDestination(),
            configService = FakeConfigService(
                autoDataCaptureBehavior = FakeAutoDataCaptureBehavior(uiLoadTracingTraceAll = false)
            ),
            startupClassifier = StartupClassifierImpl(),
        )

        assertNotNull(module.uiLoadDataListener)
        assertNotNull(module.activityLoadEventEmitter)
    }
}

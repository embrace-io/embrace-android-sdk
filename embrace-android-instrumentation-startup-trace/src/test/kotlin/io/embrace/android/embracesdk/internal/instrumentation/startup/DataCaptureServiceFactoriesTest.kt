package io.embrace.android.embracesdk.internal.instrumentation.startup

import io.embrace.android.embracesdk.fakes.FakeClock
import io.embrace.android.embracesdk.fakes.FakeConfigService
import io.embrace.android.embracesdk.fakes.FakeErrorHandler
import io.embrace.android.embracesdk.fakes.FakeTelemetryDestination
import io.embrace.android.embracesdk.internal.arch.startup.StartupClassifierImpl
import io.embrace.android.embracesdk.internal.config.ConfigService
import io.embrace.android.embracesdk.internal.config.resolved.AutoDataCaptureConfig
import io.embrace.android.embracesdk.internal.config.resolved.EmbraceConfig
import io.embrace.android.embracesdk.internal.instrumentation.startup.activity.UiLoadDataListener
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

internal class DataCaptureServiceFactoriesTest {

    private val clock = FakeClock()
    private val errorHandler = FakeErrorHandler()
    private val destination = FakeTelemetryDestination()

    @Test
    fun testDefaultImplementations() {
        val configService = FakeConfigService()
        val startupService = createStartupService(destination, errorHandler) { 1 }
        val uiLoadDataListener = createUiLoadDataListener(destination, configService)
        assertEquals(1, startupService.getAppVersionStartupCounter())
        assertNotNull(uiLoadDataListener)
        assertNotNull(createStartupTracker(configService, startupService, uiLoadDataListener))
    }

    @Test
    fun `disable ui load performance capture`() {
        val configService = FakeConfigService(
            config = EmbraceConfig(autoDataCapture = { AutoDataCaptureConfig(uiLoadTracingEnabled = { false }) }),
        )
        val uiLoadDataListener = createUiLoadDataListener(destination, configService)
        assertNull(uiLoadDataListener)
        val startupService = createStartupService(destination, errorHandler) { 1 }
        assertNotNull(createStartupTracker(configService, startupService, uiLoadDataListener))
    }

    @Test
    fun `enable only selected ui load performance capture`() {
        val configService = FakeConfigService(
            config = EmbraceConfig(autoDataCapture = { AutoDataCaptureConfig(uiLoadTracingTraceAll = { false }) }),
        )
        assertNotNull(createUiLoadDataListener(destination, configService))
    }

    private fun createStartupTracker(
        configService: ConfigService,
        startupService: StartupService,
        uiLoadDataListener: UiLoadDataListener?,
    ): StartupTracker = createStartupTracker(
        clock = clock,
        errorHandler = errorHandler,
        configService = configService,
        appStartupDataCollector = createAppStartupDataCollector(
            clock = clock,
            errorHandler = errorHandler,
            destination = destination,
            configService = configService,
            startupService = startupService,
            startupClassifier = StartupClassifierImpl(),
        ),
        uiLoadDataListener = uiLoadDataListener,
    )
}

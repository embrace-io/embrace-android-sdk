package io.embrace.android.embracesdk

import io.embrace.android.embracesdk.fakes.FakeClock
import io.embrace.android.embracesdk.fakes.FakeCurrentSessionSpan
import io.embrace.android.embracesdk.fakes.FakeOpenTelemetryModule
import io.embrace.android.embracesdk.fakes.injection.FakeAndroidServicesModule
import io.embrace.android.embracesdk.fakes.injection.FakeAnrModule
import io.embrace.android.embracesdk.fakes.injection.FakeCoreModule
import io.embrace.android.embracesdk.fakes.injection.FakeEssentialServiceModule
import io.embrace.android.embracesdk.fakes.injection.FakeInitModule
import io.embrace.android.embracesdk.fakes.injection.FakeSystemServiceModule
import io.embrace.android.embracesdk.fakes.injection.FakeWorkerThreadModule
import io.embrace.android.embracesdk.internal.arch.SessionType
import io.embrace.android.embracesdk.internal.capture.memory.EmbraceMemoryService
import io.embrace.android.embracesdk.internal.injection.DataSourceModule
import io.embrace.android.embracesdk.internal.injection.DataSourceModuleImpl
import io.mockk.unmockkAll
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

internal class EmbraceMemoryServiceTest {

    private lateinit var embraceMemoryService: EmbraceMemoryService
    private val fakeClock = FakeClock()
    private val otelModule = FakeOpenTelemetryModule()
    private lateinit var dataSourceModule: DataSourceModule

    @Before
    fun setUp() {
        fakeClock.setCurrentTime(100L)
        dataSourceModule = DataSourceModuleImpl(
            initModule = FakeInitModule(),
            coreModule = FakeCoreModule(),
            otelModule = otelModule,
            essentialServiceModule = FakeEssentialServiceModule(),
            systemServiceModule = FakeSystemServiceModule(),
            androidServicesModule = FakeAndroidServicesModule(),
            workerThreadModule = FakeWorkerThreadModule(),
            anrModule = FakeAnrModule()
        )
        dataSourceModule.dataCaptureOrchestrator.currentSessionType = SessionType.FOREGROUND
        embraceMemoryService = EmbraceMemoryService(fakeClock) { dataSourceModule }
    }

    @Test
    fun `onMemoryWarning populates events up to MAX_CAPTURED_MEMORY_WARNINGS`() {
        with(embraceMemoryService) {
            repeat(EmbraceMemoryService.MAX_CAPTURED_MEMORY_WARNINGS) {
                onMemoryWarning()
                fakeClock.tick()
            }
            val currentSessionSpan = otelModule.currentSessionSpan as FakeCurrentSessionSpan
            assertEquals(
                EmbraceMemoryService.MAX_CAPTURED_MEMORY_WARNINGS,
                currentSessionSpan.addedEvents.size
            )
            onMemoryWarning()
            assertEquals(
                EmbraceMemoryService.MAX_CAPTURED_MEMORY_WARNINGS,
                currentSessionSpan.addedEvents.size
            )
        }
    }

    @After
    fun tearDown() {
        unmockkAll()
    }
}

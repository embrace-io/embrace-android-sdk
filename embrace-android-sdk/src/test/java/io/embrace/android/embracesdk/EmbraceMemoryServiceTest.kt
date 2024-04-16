package io.embrace.android.embracesdk

import io.embrace.android.embracesdk.capture.memory.EmbraceMemoryService
import io.embrace.android.embracesdk.fakes.FakeClock
import io.embrace.android.embracesdk.fakes.FakeOpenTelemetryModule
import io.embrace.android.embracesdk.fakes.injection.FakeAndroidServicesModule
import io.embrace.android.embracesdk.fakes.injection.FakeCoreModule
import io.embrace.android.embracesdk.fakes.injection.FakeEssentialServiceModule
import io.embrace.android.embracesdk.fakes.injection.FakeInitModule
import io.embrace.android.embracesdk.fakes.injection.FakeSystemServiceModule
import io.embrace.android.embracesdk.fakes.injection.FakeWorkerThreadModule
import io.embrace.android.embracesdk.injection.DataSourceModule
import io.embrace.android.embracesdk.injection.DataSourceModuleImpl
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
        )
        embraceMemoryService = EmbraceMemoryService(fakeClock) { dataSourceModule }
    }

    @After
    fun tearDown() {
        unmockkAll()
    }

    @Test
    fun `onMemoryWarning populates memoryTimestamps if the offset is less than 100`() {
        with(embraceMemoryService) {
            repeat(100) {
                onMemoryWarning()
                fakeClock.tick()
            }
            val result = this.getCapturedData()
            assertEquals(result.size, 100)
            onMemoryWarning()
            assertEquals(result.size, 100)
        }
    }

    @Test
    fun testCleanCollections() {
        embraceMemoryService.onMemoryWarning()
        assertEquals(1, embraceMemoryService.getCapturedData().size)
        embraceMemoryService.cleanCollections()
        assertEquals(0, embraceMemoryService.getCapturedData().size)
    }
}

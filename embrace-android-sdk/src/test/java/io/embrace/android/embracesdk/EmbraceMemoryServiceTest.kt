package io.embrace.android.embracesdk

import io.embrace.android.embracesdk.capture.memory.EmbraceMemoryService
import io.embrace.android.embracesdk.fakes.FakeClock
import io.embrace.android.embracesdk.fakes.FakeOpenTelemetryModule
import io.embrace.android.embracesdk.fakes.injection.FakeAndroidServicesModule
import io.embrace.android.embracesdk.fakes.injection.FakeEssentialServiceModule
import io.embrace.android.embracesdk.fakes.injection.FakeInitModule
import io.embrace.android.embracesdk.fakes.injection.FakeSystemServiceModule
import io.embrace.android.embracesdk.fakes.injection.FakeWorkerThreadModule
import io.embrace.android.embracesdk.injection.DataSourceModule
import io.embrace.android.embracesdk.injection.DataSourceModuleImpl
import io.embrace.android.embracesdk.fakes.injection.FakeCoreModule
import io.mockk.unmockkAll
import org.junit.After
import org.junit.Before

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
}

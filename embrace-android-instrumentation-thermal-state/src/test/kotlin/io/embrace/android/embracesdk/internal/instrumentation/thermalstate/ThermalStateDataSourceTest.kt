package io.embrace.android.embracesdk.internal.instrumentation.thermalstate

import android.os.PowerManager
import io.embrace.android.embracesdk.fakes.FakeClock
import io.embrace.android.embracesdk.fakes.FakeEmbLogger
import io.embrace.android.embracesdk.fakes.FakeTraceWriter
import io.embrace.android.embracesdk.fakes.fakeBackgroundWorker
import io.embrace.android.embracesdk.internal.arch.schema.EmbType
import io.mockk.mockk
import io.mockk.verify
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

internal class ThermalStateDataSourceTest {

    private lateinit var dataSource: ThermalStateDataSource
    private lateinit var traceWriter: FakeTraceWriter
    private val mockPowerManager = mockk<PowerManager>(relaxed = true)

    @Before
    fun setUp() {
        traceWriter = FakeTraceWriter()
        dataSource = ThermalStateDataSource(
            traceWriter,
            FakeEmbLogger(),
            fakeBackgroundWorker(),
            FakeClock(100),
        ) { mockPowerManager }
    }

    @Test
    fun onThermalStateChanged() {
        with(dataSource) {
            handleThermalStateChange(PowerManager.THERMAL_STATUS_NONE)
            handleThermalStateChange(PowerManager.THERMAL_STATUS_SEVERE)
            handleThermalStateChange(PowerManager.THERMAL_STATUS_CRITICAL)
        }
        assertEquals(3, traceWriter.createdSpans.size)
        traceWriter.createdSpans.forEach {
            assertEquals(EmbType.Performance.ThermalState, it.type)
        }
        assertEquals(PowerManager.THERMAL_STATUS_NONE, traceWriter.createdSpans[0].attributes["status"]?.toInt())
        assertEquals(PowerManager.THERMAL_STATUS_SEVERE, traceWriter.createdSpans[1].attributes["status"]?.toInt())
        assertEquals(PowerManager.THERMAL_STATUS_CRITICAL, traceWriter.createdSpans[2].attributes["status"]?.toInt())
    }

    @Test
    fun onLimitExceeded() {
        repeat(250) {
            dataSource.handleThermalStateChange(PowerManager.THERMAL_STATUS_SEVERE)
        }

        assertEquals(100, traceWriter.createdSpans.size)
    }

    @Test
    fun onEnableAndDisable() {
        verify(exactly = 0) { mockPowerManager.addThermalStatusListener(any(), any()) }
        dataSource.enableDataCapture()
        verify(exactly = 1) { mockPowerManager.addThermalStatusListener(any(), any()) }
        dataSource.disableDataCapture()
        verify(exactly = 1) { mockPowerManager.removeThermalStatusListener(any()) }
    }
}

package io.embrace.android.embracesdk.internal.instrumentation.thermalstate

import android.os.PowerManager
import io.embrace.android.embracesdk.fakes.FakeInstrumentationArgs
import io.embrace.android.embracesdk.internal.arch.schema.EmbType
import io.mockk.mockk
import io.mockk.verify
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

internal class ThermalStateDataSourceTest {

    private lateinit var dataSource: ThermalStateDataSource
    private lateinit var args: FakeInstrumentationArgs
    private val mockPowerManager = mockk<PowerManager>(relaxed = true)

    @Before
    fun setUp() {
        args = FakeInstrumentationArgs(mockk(), systemServiceSupplier = { mockPowerManager })
        dataSource = ThermalStateDataSource(args)
    }

    @Test
    fun onThermalStateChanged() {
        with(dataSource) {
            handleThermalStateChange(PowerManager.THERMAL_STATUS_NONE)
            handleThermalStateChange(PowerManager.THERMAL_STATUS_SEVERE)
            handleThermalStateChange(PowerManager.THERMAL_STATUS_CRITICAL)
        }
        val destination = args.destination
        assertEquals(3, destination.createdSpans.size)
        destination.createdSpans.forEach {
            assertEquals(EmbType.Performance.ThermalState, it.type)
        }
        assertEquals(PowerManager.THERMAL_STATUS_NONE, destination.createdSpans[0].attributes["status"]?.toInt())
        assertEquals(PowerManager.THERMAL_STATUS_SEVERE, destination.createdSpans[1].attributes["status"]?.toInt())
        assertEquals(PowerManager.THERMAL_STATUS_CRITICAL, destination.createdSpans[2].attributes["status"]?.toInt())
    }

    @Test
    fun onLimitExceeded() {
        repeat(250) {
            dataSource.handleThermalStateChange(PowerManager.THERMAL_STATUS_SEVERE)
        }

        assertEquals(100, args.destination.createdSpans.size)
    }

    @Test
    fun onEnableAndDisable() {
        verify(exactly = 0) { mockPowerManager.addThermalStatusListener(any(), any()) }
        dataSource.onDataCaptureEnabled()
        verify(exactly = 1) { mockPowerManager.addThermalStatusListener(any(), any()) }
        dataSource.onDataCaptureDisabled()
        verify(exactly = 1) { mockPowerManager.removeThermalStatusListener(any()) }
    }
}

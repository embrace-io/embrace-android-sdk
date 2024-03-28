package io.embrace.android.embracesdk

import android.os.PowerManager
import io.embrace.android.embracesdk.capture.thermalstate.EmbraceThermalStatusService
import io.embrace.android.embracesdk.concurrency.BlockableExecutorService
import io.embrace.android.embracesdk.fakes.system.mockPowerManager
import io.embrace.android.embracesdk.payload.ThermalState
import io.embrace.android.embracesdk.worker.BackgroundWorker
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

internal class EmbraceThermalStatusServiceTest {

    private lateinit var service: EmbraceThermalStatusService

    @Before
    fun setUp() {
        service = EmbraceThermalStatusService(
            BackgroundWorker(BlockableExecutorService()),
            { 0 }
        ) { mockPowerManager() }
    }

    @Test
    fun onThermalStatusChanged() {
        with(service) {
            handleThermalStateChange(PowerManager.THERMAL_STATUS_NONE)
            handleThermalStateChange(PowerManager.THERMAL_STATUS_SEVERE)
            handleThermalStateChange(PowerManager.THERMAL_STATUS_CRITICAL)
        }
        val states = service.getCapturedData()
        assertEquals(3, states.size)
        assertEquals(ThermalState(0, PowerManager.THERMAL_STATUS_NONE), states[0])
        assertEquals(ThermalState(0, PowerManager.THERMAL_STATUS_SEVERE), states[1])
        assertEquals(ThermalState(0, PowerManager.THERMAL_STATUS_CRITICAL), states[2])
    }

    @Test
    fun onLimitExceeded() {
        repeat(250) {
            service.handleThermalStateChange(PowerManager.THERMAL_STATUS_SEVERE)
        }

        val states = service.getCapturedData()
        assertEquals(100, states.size)
    }

    @Test
    fun testCleanCollections() {
        service.handleThermalStateChange(PowerManager.THERMAL_STATUS_CRITICAL)
        assertEquals(1, service.getCapturedData().size)
        service.cleanCollections()
        assertEquals(0, service.getCapturedData().size)
    }
}

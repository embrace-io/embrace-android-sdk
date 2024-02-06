package io.embrace.android.embracesdk

import android.os.PowerManager
import com.google.common.util.concurrent.MoreExecutors
import io.embrace.android.embracesdk.capture.thermalstate.EmbraceThermalStatusService
import io.embrace.android.embracesdk.fakes.system.mockPowerManager
import io.embrace.android.embracesdk.logging.InternalEmbraceLogger
import io.embrace.android.embracesdk.payload.ThermalState
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

internal class EmbraceThermalStatusServiceTest {

    private lateinit var service: EmbraceThermalStatusService

    @Before
    fun setUp() {
        service = EmbraceThermalStatusService(
            MoreExecutors.directExecutor(),
            { 0 },
            InternalEmbraceLogger(),
            mockPowerManager()
        )
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

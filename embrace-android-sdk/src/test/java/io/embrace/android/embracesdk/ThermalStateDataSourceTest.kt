package io.embrace.android.embracesdk

import android.os.PowerManager
import io.embrace.android.embracesdk.arch.schema.EmbType
import io.embrace.android.embracesdk.capture.thermalstate.ThermalStateDataSource
import io.embrace.android.embracesdk.concurrency.BlockableExecutorService
import io.embrace.android.embracesdk.fakes.FakeClock
import io.embrace.android.embracesdk.fakes.FakeSpanService
import io.embrace.android.embracesdk.fakes.system.mockPowerManager
import io.embrace.android.embracesdk.logging.InternalEmbraceLogger
import io.embrace.android.embracesdk.worker.BackgroundWorker
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

internal class ThermalStateDataSourceTest {

    private lateinit var dataSource: ThermalStateDataSource
    private lateinit var spanWriter: FakeSpanService

    @Before
    fun setUp() {
        spanWriter = FakeSpanService()
        dataSource = ThermalStateDataSource(
            spanWriter,
            InternalEmbraceLogger(),
            BackgroundWorker(BlockableExecutorService()),
            FakeClock(100),
        ) { mockPowerManager() }
    }

    @Test
    fun onThermalStatusChanged() {
        with(dataSource) {
            handleThermalStateChange(PowerManager.THERMAL_STATUS_NONE)
            handleThermalStateChange(PowerManager.THERMAL_STATUS_SEVERE)
            handleThermalStateChange(PowerManager.THERMAL_STATUS_CRITICAL)
        }
        assertEquals(3, spanWriter.createdSpans.size)
        spanWriter.createdSpans.forEach {
            assertEquals(EmbType.Performance.ThermalState, it.type)
        }
        assertEquals(PowerManager.THERMAL_STATUS_NONE, spanWriter.createdSpans[0].attributes["status"]?.toInt())
        assertEquals(PowerManager.THERMAL_STATUS_SEVERE, spanWriter.createdSpans[1].attributes["status"]?.toInt())
        assertEquals(PowerManager.THERMAL_STATUS_CRITICAL, spanWriter.createdSpans[2].attributes["status"]?.toInt())
    }

    @Test
    fun onLimitExceeded() {
        repeat(250) {
            dataSource.handleThermalStateChange(PowerManager.THERMAL_STATUS_SEVERE)
        }

        assertEquals(100, spanWriter.createdSpans.size)
    }
}

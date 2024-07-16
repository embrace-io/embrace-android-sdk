package io.embrace.android.embracesdk

import android.os.PowerManager
import io.embrace.android.embracesdk.concurrency.BlockableExecutorService
import io.embrace.android.embracesdk.fakes.FakeClock
import io.embrace.android.embracesdk.fakes.FakeSpanService
import io.embrace.android.embracesdk.fakes.system.mockPowerManager
import io.embrace.android.embracesdk.internal.arch.schema.EmbType
import io.embrace.android.embracesdk.internal.capture.thermalstate.ThermalStateDataSource
import io.embrace.android.embracesdk.internal.logging.EmbLoggerImpl
import io.embrace.android.embracesdk.internal.worker.BackgroundWorker
import io.mockk.verify
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

internal class ThermalStateDataSourceTest {

    private lateinit var dataSource: ThermalStateDataSource
    private lateinit var spanWriter: FakeSpanService
    private val mockPowerManager = mockPowerManager()

    @Before
    fun setUp() {
        spanWriter = FakeSpanService()
        dataSource = ThermalStateDataSource(
            spanWriter,
            EmbLoggerImpl(),
            BackgroundWorker(BlockableExecutorService()),
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

    @Test
    fun onEnableAndDisable() {
        verify(exactly = 0) { mockPowerManager.addThermalStatusListener(any(), any()) }
        dataSource.enableDataCapture()
        verify(exactly = 1) { mockPowerManager.addThermalStatusListener(any(), any()) }
        dataSource.disableDataCapture()
        verify(exactly = 1) { mockPowerManager.removeThermalStatusListener(any()) }
    }
}

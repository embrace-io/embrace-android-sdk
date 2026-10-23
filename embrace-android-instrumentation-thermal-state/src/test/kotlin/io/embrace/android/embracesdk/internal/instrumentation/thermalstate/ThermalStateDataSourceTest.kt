package io.embrace.android.embracesdk.internal.instrumentation.thermalstate

import android.os.PowerManager
import io.embrace.android.embracesdk.concurrency.BlockingScheduledExecutorService
import io.embrace.android.embracesdk.fakes.FakeInstrumentationArgs
import io.embrace.android.embracesdk.internal.arch.schema.EmbType
import io.embrace.android.embracesdk.internal.worker.BackgroundWorker
import io.embrace.android.embracesdk.semconv.EmbThermalStateAttributes
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

internal class ThermalStateDataSourceTest {

    private lateinit var dataSource: ThermalStateDataSource
    private lateinit var args: FakeInstrumentationArgs
    private val notifier = FakeThermalStatusNotifier()

    @Before
    fun setUp() {
        args = FakeInstrumentationArgs()
        dataSource = ThermalStateDataSource(args) { notifier }
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
        assertEquals(PowerManager.THERMAL_STATUS_NONE, destination.createdSpans[0].attributes[EmbThermalStateAttributes.STATUS]?.toInt())
        assertEquals(PowerManager.THERMAL_STATUS_SEVERE, destination.createdSpans[1].attributes[EmbThermalStateAttributes.STATUS]?.toInt())
        assertEquals(
            PowerManager.THERMAL_STATUS_CRITICAL,
            destination.createdSpans[2].attributes[EmbThermalStateAttributes.STATUS]?.toInt(),
        )
    }

    @Test
    fun onLimitExceeded() {
        repeat(250) {
            dataSource.handleThermalStateChange(PowerManager.THERMAL_STATUS_SEVERE)
        }

        assertEquals(100, args.destination.createdSpans.size)
    }

    @Test
    fun `power manager is not retrieved until data capture is enabled`() {
        var systemServiceLookups = 0
        val countingArgs = FakeInstrumentationArgs(
            systemServiceSupplier = {
                systemServiceLookups++
                null
            },
        )
        val source = ThermalStateDataSource(countingArgs)
        assertEquals(0, systemServiceLookups)

        source.onDataCaptureEnabled()
        assertEquals(1, systemServiceLookups)
    }

    @Test
    fun onEnable() {
        assertTrue(notifier.addedListeners.isEmpty())
        dataSource.onDataCaptureEnabled()
        assertEquals(1, notifier.addedListeners.size)
    }

    @Test
    fun `disable removes the registered listener`() {
        dataSource.onDataCaptureEnabled()
        val listener = notifier.addedListeners.single()

        dataSource.onDataCaptureDisabled()
        assertEquals(listOf(listener), notifier.removedListeners)

        dataSource.onDataCaptureDisabled()
        assertEquals(1, notifier.removedListeners.size)
    }

    @Test
    fun `disable before queued registration runs prevents registration`() {
        val executor = BlockingScheduledExecutorService(blockingMode = true)
        val blockingArgs = FakeInstrumentationArgs(
            backgroundWorkerSupplier = { BackgroundWorker(executor) },
        )
        val source = ThermalStateDataSource(blockingArgs) { notifier }
        source.onDataCaptureEnabled()
        source.onDataCaptureDisabled()
        executor.runCurrentlyBlocked()

        assertTrue(notifier.addedListeners.isEmpty())
        assertTrue(notifier.removedListeners.isEmpty())
    }
}

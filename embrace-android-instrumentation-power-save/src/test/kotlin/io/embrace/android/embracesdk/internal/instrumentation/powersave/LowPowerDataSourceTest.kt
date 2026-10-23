package io.embrace.android.embracesdk.internal.instrumentation.powersave

import android.content.Intent
import android.os.PowerManager
import io.embrace.android.embracesdk.concurrency.BlockingScheduledExecutorService
import io.embrace.android.embracesdk.fakes.FakeInstrumentationArgs
import io.embrace.android.embracesdk.fakes.fakeBackgroundWorker
import io.embrace.android.embracesdk.internal.arch.attrs.asPair
import io.embrace.android.embracesdk.internal.arch.schema.EmbType
import io.embrace.android.embracesdk.internal.worker.BackgroundWorker
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

internal class LowPowerDataSourceTest {

    private lateinit var dataSource: LowPowerDataSource
    private lateinit var args: FakeInstrumentationArgs
    private lateinit var application: FakeApplication
    private var powerSaveMode = false

    @Before
    fun setUp() {
        application = FakeApplication()
        args = FakeInstrumentationArgs(application = application)
        dataSource = LowPowerDataSource(
            args,
            fakeBackgroundWorker(),
        ) { PowerSaveModeSource { powerSaveMode } }.apply(LowPowerDataSource::onDataCaptureEnabled)
    }

    @Test
    fun `span recorded`() {
        dataSource.onPowerSaveModeChanged(true)
        dataSource.onPowerSaveModeChanged(false)
        assertSpanAdded()
    }

    @Test
    fun `unbalanced calls ignored recorded`() {
        dataSource.onPowerSaveModeChanged(true)
        dataSource.onPowerSaveModeChanged(true)
        dataSource.onPowerSaveModeChanged(false)
        dataSource.onPowerSaveModeChanged(false)
        assertSpanAdded()
    }

    @Test
    fun `no span recorded for unbalanced calls`() {
        dataSource.onPowerSaveModeChanged(false)
        assertEquals(0, args.destination.createdSpans.size)
    }

    @Test
    fun `limits respected`() {
        repeat(150) {
            dataSource.onPowerSaveModeChanged(true)
            dataSource.onPowerSaveModeChanged(false)
        }
        assertEquals(100, args.destination.createdSpans.count { it.type == EmbType.System.LowPower })
    }

    @Test
    fun `receive intent`() {
        val intent = object : Intent() {
            override fun getAction(): String = PowerManager.ACTION_POWER_SAVE_MODE_CHANGED
        }
        val receiver = application.receivers.single()
        powerSaveMode = true
        receiver.onReceive(application, intent)
        powerSaveMode = false
        receiver.onReceive(application, intent)
        assertSpanAdded()
    }

    private fun assertSpanAdded() {
        val span = args.destination.createdSpans.single()
        assertEquals(EmbType.System.LowPower, span.type)
        assertEquals("device-low-power", span.name)
        assertEquals(
            mapOf(EmbType.System.LowPower.asPair()),
            span.attributes,
        )
    }

    @Test
    fun `receiver is unregistered when data capture is disabled`() {
        assertTrue(hasPowerSaveReceiver())
        dataSource.onDataCaptureDisabled()
        assertFalse(hasPowerSaveReceiver())
    }

    @Test
    fun `pending registration is dropped when data capture is disabled first`() {
        val executor = BlockingScheduledExecutorService()
        val pending = LowPowerDataSource(
            FakeInstrumentationArgs(application = application),
            BackgroundWorker(executor),
        ) { PowerSaveModeSource { false } }
        dataSource.onDataCaptureDisabled()

        pending.onDataCaptureEnabled()
        pending.onDataCaptureDisabled()
        executor.runCurrentlyBlocked()
        assertFalse(hasPowerSaveReceiver())
    }

    private fun hasPowerSaveReceiver(): Boolean = application.receivers.any { it is PowerSaveModeReceiver }
}

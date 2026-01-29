package io.embrace.android.embracesdk.internal.instrumentation.powersave

import android.content.Context
import android.content.Intent
import android.os.PowerManager
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.embrace.android.embracesdk.RobolectricTest
import io.embrace.android.embracesdk.fakes.FakeInstrumentationArgs
import io.embrace.android.embracesdk.fakes.fakeBackgroundWorker
import io.embrace.android.embracesdk.internal.arch.attrs.asPair
import io.embrace.android.embracesdk.internal.arch.schema.EmbType
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RuntimeEnvironment

@RunWith(AndroidJUnit4::class)
internal class LowPowerDataSourceTest : RobolectricTest() {

    private lateinit var dataSource: LowPowerDataSource
    private lateinit var args: FakeInstrumentationArgs

    @Before
    fun setUp() {
        args = FakeInstrumentationArgs(
            application = ApplicationProvider.getApplicationContext()
        )
        dataSource = LowPowerDataSource(
            args,
            fakeBackgroundWorker(),
        ) { mockk(relaxed = true) }.apply(LowPowerDataSource::onDataCaptureEnabled)
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
    fun `send intent`() {
        val ctx: Context = ApplicationProvider.getApplicationContext()
        val intent = Intent(PowerManager.ACTION_POWER_SAVE_MODE_CHANGED)
        RuntimeEnvironment.getApplication().sendBroadcast(intent)
        ctx.sendBroadcast(intent)
    }

    private fun assertSpanAdded() {
        val span = args.destination.createdSpans.single()
        assertEquals(EmbType.System.LowPower, span.type)
        assertEquals("device-low-power", span.name)
        assertEquals(
            mapOf(EmbType.System.LowPower.asPair()),
            span.attributes
        )
    }
}

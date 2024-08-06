package io.embrace.android.embracesdk.internal.capture.powersave

import android.content.Context
import android.content.Intent
import android.os.PowerManager
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.util.concurrent.MoreExecutors
import io.embrace.android.embracesdk.fakes.FakeClock
import io.embrace.android.embracesdk.fakes.FakeSpanService
import io.embrace.android.embracesdk.internal.arch.schema.EmbType
import io.embrace.android.embracesdk.internal.logging.EmbLoggerImpl
import io.embrace.android.embracesdk.internal.worker.BackgroundWorker
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RuntimeEnvironment

@RunWith(AndroidJUnit4::class)
internal class LowPowerDataSourceTest {

    private lateinit var dataSource: LowPowerDataSource
    private lateinit var spanService: FakeSpanService

    @Before
    fun setUp() {
        spanService = FakeSpanService()
        dataSource = LowPowerDataSource(
            ApplicationProvider.getApplicationContext(),
            spanService,
            EmbLoggerImpl(),
            BackgroundWorker(MoreExecutors.newDirectExecutorService()),
            FakeClock()
        ) { mockk(relaxed = true) }.apply {
            enableDataCapture()
        }
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
        assertEquals(0, spanService.createdSpans.size)
    }

    @Test
    fun `limits respected`() {
        repeat(150) {
            dataSource.onPowerSaveModeChanged(true)
            dataSource.onPowerSaveModeChanged(false)
        }
        assertEquals(100, spanService.createdSpans.count { it.type == EmbType.System.LowPower })
    }

    @Test
    fun `send intent`() {
        val ctx: Context = ApplicationProvider.getApplicationContext()
        val intent = Intent(PowerManager.ACTION_POWER_SAVE_MODE_CHANGED)
        RuntimeEnvironment.getApplication().sendBroadcast(intent)
        ctx.sendBroadcast(intent)
    }

    private fun assertSpanAdded() {
        val span = spanService.createdSpans.single()
        assertEquals(EmbType.System.LowPower, span.type)
        assertEquals("device-low-power", span.name)
        assertEquals(
            mapOf(EmbType.System.LowPower.toEmbraceKeyValuePair()),
            span.attributes
        )
    }
}

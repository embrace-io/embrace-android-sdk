package io.embrace.android.embracesdk.internal.envelope.resource

import android.view.WindowManager
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.embrace.android.embracesdk.concurrency.BlockingScheduledExecutorService
import io.embrace.android.embracesdk.fakes.FakeInternalLogger
import io.embrace.android.embracesdk.internal.SystemInfo
import io.embrace.android.embracesdk.internal.worker.BackgroundWorker
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The values [DeviceImpl] retrieves asynchronously start out as placeholders, so anything holding
 * a resource built before they land needs to be told when they change.
 */
@RunWith(AndroidJUnit4::class)
internal class DeviceImplChangeListenerTest {

    @Test
    fun `listeners are notified once each async value lands`() {
        val executor = BlockingScheduledExecutorService(blockingMode = true)
        val device = createDevice(executor)

        var count = 0
        device.addChangeListener { count++ }

        // nothing has run yet, so the placeholders are still in place
        assertEquals(0, count)
        assertEquals("", device.screenResolution)

        executor.runCurrentlyBlocked()
        assertEquals(3, count)
        assertEquals("0x0", device.screenResolution)
    }

    @Test
    fun `a listener registered after the async work has run is not notified`() {
        val executor = BlockingScheduledExecutorService(blockingMode = true)
        val device = createDevice(executor)
        executor.runCurrentlyBlocked()

        var count = 0
        device.addChangeListener { count++ }
        assertEquals(0, count)
    }

    @Test
    fun `a throwing listener does not prevent the others from being notified`() {
        val executor = BlockingScheduledExecutorService(blockingMode = true)
        val device = createDevice(executor)

        var count = 0
        device.addChangeListener { error("listener failed") }
        device.addChangeListener { count++ }

        executor.runCurrentlyBlocked()
        assertEquals(3, count)
    }

    private fun createDevice(executor: BlockingScheduledExecutorService) = DeviceImpl(
        windowManagerProvider = { mockk<WindowManager>(relaxed = true) },
        backgroundWorker = BackgroundWorker(executor),
        systemInfo = SystemInfo(),
        logger = FakeInternalLogger(),
    )
}

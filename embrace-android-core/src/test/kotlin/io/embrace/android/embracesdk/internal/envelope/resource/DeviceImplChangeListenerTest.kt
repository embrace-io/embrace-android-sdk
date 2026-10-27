package io.embrace.android.embracesdk.internal.envelope.resource

import android.view.WindowManager
import io.embrace.android.embracesdk.concurrency.BlockingScheduledExecutorService
import io.embrace.android.embracesdk.fakes.FakeErrorHandler
import io.embrace.android.embracesdk.internal.SystemInfo
import io.embrace.android.embracesdk.internal.worker.BackgroundWorker
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The values [DeviceImpl] retrieves asynchronously start out as placeholders, so anything holding
 * a resource built before they land needs to be told when they change.
 */
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
        val device = createDevice(executor, FakeErrorHandler(throwOnSdkError = false))

        var count = 0
        device.addChangeListener { error("listener failed") }
        device.addChangeListener { count++ }

        executor.runCurrentlyBlocked()
        assertEquals(3, count)
    }

    @Test
    fun `listeners are notified when the window manager can't be retrieved`() {
        val executor = BlockingScheduledExecutorService(blockingMode = true)
        val device = createDevice(
            executor = executor,
            errorHandler = FakeErrorHandler(throwOnSdkError = false),
            windowManagerProvider = { error("no window manager") },
        )

        var count = 0
        device.addChangeListener { count++ }

        executor.runCurrentlyBlocked()
        assertEquals(3, count)
        assertEquals("", device.screenResolution)
    }

    private fun createDevice(
        executor: BlockingScheduledExecutorService,
        errorHandler: FakeErrorHandler = FakeErrorHandler(),
        windowManagerProvider: () -> WindowManager? = { null },
    ) = DeviceImpl(
        windowManagerProvider = windowManagerProvider,
        backgroundWorker = BackgroundWorker(executor),
        systemInfo = SystemInfo(),
        errorHandler = errorHandler,
        isEmulator = { false },
    ).apply { startAsyncRetrieval() }
}

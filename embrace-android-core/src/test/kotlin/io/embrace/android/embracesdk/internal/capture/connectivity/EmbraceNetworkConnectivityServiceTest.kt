@file:Suppress("DEPRECATION", "OVERRIDE_DEPRECATION")

package io.embrace.android.embracesdk.internal.capture.connectivity

import android.content.BroadcastReceiver
import android.content.ContextWrapper
import android.content.Intent
import android.content.IntentFilter
import android.net.ConnectivityManager
import android.net.NetworkInfo
import io.embrace.android.embracesdk.concurrency.BlockingScheduledExecutorService
import io.embrace.android.embracesdk.fakes.fakeBackgroundWorker
import io.embrace.android.embracesdk.internal.logging.ErrorHandler
import io.embrace.android.embracesdk.internal.logging.ErrorHandlerImpl
import io.embrace.android.embracesdk.internal.worker.BackgroundWorker
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

internal class EmbraceNetworkConnectivityServiceTest {

    private lateinit var service: EmbraceNetworkConnectivityService
    private lateinit var testListener: NetworkConnectivityListener
    private var status: ConnectivityStatus? = null
    private lateinit var context: ReceiverRecordingContext
    private lateinit var errorHandler: ErrorHandler
    private lateinit var worker: BackgroundWorker
    private var networkInfo: () -> NetworkInfo? = { null }

    @Before
    fun setup() {
        context = ReceiverRecordingContext()
        errorHandler = ErrorHandlerImpl()
        worker = fakeBackgroundWorker()
        service = createService()

        testListener = NetworkConnectivityListener { status -> this@EmbraceNetworkConnectivityServiceTest.status = status }
        service.addNetworkConnectivityListener(testListener)
    }

    /**
     * Asserts that a network connectivity broadcast receiver can be registered/unregistered
     */
    @Test
    @Throws(InterruptedException::class)
    fun `test connectivity broadcast receiver can register and unregister`() {
        service.register()
        assertEquals(listOf(service), context.registered)
        service.close()
        assertEquals(listOf(service), context.unregistered)
    }

    @Test
    fun `test listener get notified when connectivity status changes to WIFI`() {
        // call onReceive to emulate a connectivity status change
        networkInfo = { fakeNetworkInfo(true, ConnectivityManager.TYPE_WIFI) }
        service.onReceive(context, Intent())
        assertEquals(OptimisticWifi, status)
    }

    @Test
    fun `test listener get notified when connectivity status changes to MOBILE`() {
        // call onReceive to emulate a connectivity status change
        networkInfo = { fakeNetworkInfo(true, ConnectivityManager.TYPE_MOBILE) }
        service.onReceive(context, Intent())
        assertEquals(OptimisticWan, status)
    }

    @Test
    fun `test listener get notified when connectivity status changes to no connectivity`() {
        // call onReceive to emulate a connectivity status change
        networkInfo = { fakeNetworkInfo(false, ConnectivityManager.TYPE_WIFI) }
        service.onReceive(context, Intent())
        assertEquals(ConnectivityStatus.None, status)
    }

    @Test
    fun `test listener get notified when connectivity status changes and no info obtained`() {
        // call onReceive to emulate a connectivity status change
        networkInfo = { throw IllegalStateException() }
        service.onReceive(context, Intent())
        assertEquals(OptimisticUnknown, status)
    }

    @Test
    fun `test listener get notified when connectivity status changes and not notified when removed`() {
        // call onReceive to emulate a connectivity status change
        networkInfo = { fakeNetworkInfo(true, ConnectivityManager.TYPE_MOBILE) }
        service.onReceive(context, Intent())

        assertEquals(OptimisticWan, status)

        // remove listener and call onReceive again
        service.removeNetworkConnectivityListener(testListener)
        networkInfo = { fakeNetworkInfo(true, ConnectivityManager.TYPE_WIFI) }
        service.onReceive(context, Intent())

        assertEquals(OptimisticWan, status)
    }

    @Test
    fun `network is assumed available when connectivity manager is unavailable`() {
        var observed: ConnectivityStatus? = null
        createService(connectivityAvailable = false).apply {
            addNetworkConnectivityListener { observed = it }
            assertEquals(ConnectivityStatus.Unverified, observed)
            assertTrue(checkNotNull(observed).isConnected)

            // a broadcast must not downgrade the status to None
            onReceive(context, Intent())
            assertEquals(ConnectivityStatus.Unverified, observed)
        }
    }

    @Test
    fun `close without register does not unregister the receiver`() {
        val service = createService()
        service.close()
        assertEquals(emptyList<BroadcastReceiver>(), context.unregistered)
    }

    @Test
    fun `close before queued register runs prevents registration`() {
        val executor = BlockingScheduledExecutorService(blockingMode = true)
        val service = EmbraceNetworkConnectivityService(
            context,
            BackgroundWorker(executor),
            errorHandler,
            { true },
            { networkInfo() },
        )
        service.register()
        service.close()
        executor.runCurrentlyBlocked()

        assertEquals(emptyList<BroadcastReceiver>(), context.registered)
        assertEquals(emptyList<BroadcastReceiver>(), context.unregistered)
    }

    @Test
    fun `connectivity is not read when service is constructed`() {
        var read = false
        EmbraceNetworkConnectivityService(
            context,
            worker,
            errorHandler,
            { true.also { read = true } },
            { networkInfo().also { read = true } },
        )
        assertFalse(read)
    }

    private fun createService(connectivityAvailable: Boolean = true) =
        EmbraceNetworkConnectivityService(context, worker, errorHandler, { connectivityAvailable }, { networkInfo() })

    private fun fakeNetworkInfo(connected: Boolean, type: Int): NetworkInfo = object : NetworkInfo(type, 0, null, null) {
        override fun isConnected(): Boolean = connected
        override fun getType(): Int = type
    }

    private class ReceiverRecordingContext : ContextWrapper(null) {
        val registered = mutableListOf<BroadcastReceiver?>()
        val unregistered = mutableListOf<BroadcastReceiver?>()

        override fun registerReceiver(receiver: BroadcastReceiver?, filter: IntentFilter?): Intent? {
            registered.add(receiver)
            return null
        }

        override fun unregisterReceiver(receiver: BroadcastReceiver?) {
            unregistered.add(receiver)
        }
    }
}

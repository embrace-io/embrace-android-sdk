package io.embrace.android.embracesdk.internal.capture.connectivity

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import com.google.common.util.concurrent.MoreExecutors
import io.embrace.android.embracesdk.fakes.FakeClock
import io.embrace.android.embracesdk.internal.comms.delivery.NetworkStatus
import io.embrace.android.embracesdk.internal.logging.EmbLogger
import io.embrace.android.embracesdk.internal.logging.EmbLoggerImpl
import io.embrace.android.embracesdk.internal.worker.BackgroundWorker
import io.mockk.clearAllMocks
import io.mockk.every
import io.mockk.mockk
import io.mockk.unmockkAll
import io.mockk.verify
import org.junit.After
import org.junit.AfterClass
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.BeforeClass
import org.junit.Test

@Suppress("DEPRECATION")
internal class EmbraceNetworkConnectivityServiceTest {

    private lateinit var service: EmbraceNetworkConnectivityService
    private var networkStatus: NetworkStatus? = null

    companion object {
        private lateinit var context: Context
        private lateinit var logger: EmbLogger
        private lateinit var mockConnectivityManager: ConnectivityManager
        private lateinit var worker: BackgroundWorker
        private lateinit var fakeClock: FakeClock

        /**
         * Setup before all tests get executed. Create mocks here.
         */
        @BeforeClass
        @JvmStatic
        fun setupBeforeAll() {
            context = mockk(relaxed = true)
            logger = EmbLoggerImpl()
            mockConnectivityManager = mockk()
            fakeClock = FakeClock()
            worker = BackgroundWorker(MoreExecutors.newDirectExecutorService())
        }

        /**
         * Setup after all tests get executed. Un-mock all here.
         */
        @AfterClass
        @JvmStatic
        fun tearDownAfterAll() {
            unmockkAll()
        }
    }

    /**
     * Setup before each test.
     */
    @Before
    fun setup() {
        every { context.getSystemService(Context.CONNECTIVITY_SERVICE) } returns mockConnectivityManager

        service = EmbraceNetworkConnectivityService(
            context,
            worker,
            logger,
            mockConnectivityManager,
        )
    }

    /**
     * Setup after each test. Clean mocks content.
     */
    @After
    fun tearDown() {
        clearAllMocks()
    }

    /**
     * Asserts that a network connectivity broadcast receiver can be registered/unregistered
     */
    @SuppressLint("UnspecifiedRegisterReceiverFlag")
    @Test
    @Throws(InterruptedException::class)
    fun `test connectivity broadcast receiver can register and unregister`() {
        service.register()
        verify { context.registerReceiver(service, any()) }
        service.close()
        verify { context.unregisterReceiver(service) }
    }

    @Test
    fun `test listener get notified when connectivity status changes to WIFI`() {
        service.addNetworkConnectivityListener {
            networkStatus = it
        }

        // call onReceive to emulate a connectivity status change
        val mockIntent = mockk<Intent>()
        every { mockConnectivityManager.activeNetworkInfo?.isConnected } returns true
        every { mockConnectivityManager.activeNetworkInfo?.type } returns ConnectivityManager.TYPE_WIFI
        service.onReceive(context, mockIntent)
        assertEquals(NetworkStatus.WIFI, networkStatus)
    }

    @Test
    fun `test listener get notified when connectivity status changes to MOBILE`() {
        service.addNetworkConnectivityListener {
            networkStatus = it
        }

        // call onReceive to emulate a connectivity status change
        val mockIntent = mockk<Intent>()
        every { mockConnectivityManager.activeNetworkInfo?.isConnected } returns true
        every { mockConnectivityManager.activeNetworkInfo?.type } returns ConnectivityManager.TYPE_MOBILE
        service.onReceive(context, mockIntent)
        assertEquals(NetworkStatus.WAN, networkStatus)
    }

    @Test
    fun `test listener get notified when connectivity status changes to no connectivity`() {
        service.addNetworkConnectivityListener {
            networkStatus = it
        }

        // call onReceive to emulate a connectivity status change
        val mockIntent = mockk<Intent>()
        every { mockConnectivityManager.activeNetworkInfo?.isConnected } returns false
        service.onReceive(context, mockIntent)
        assertEquals(NetworkStatus.NOT_REACHABLE, networkStatus)
    }

    @Test
    fun `test listener get notified when connectivity status changes and no info obtained`() {
        service.addNetworkConnectivityListener {
            networkStatus = it
        }

        // call onReceive to emulate a connectivity status change
        val mockIntent = mockk<Intent>()
        every { mockConnectivityManager.activeNetworkInfo } throws Exception("")
        service.onReceive(context, mockIntent)
        assertEquals(NetworkStatus.UNKNOWN, networkStatus)
    }

    @Test
    fun `test listener get notified when connectivity status changes and not notified when removed`() {
        val listener: (status: NetworkStatus) -> Unit = {
            networkStatus = it
        }
        service.addNetworkConnectivityListener(listener)

        // call onReceive to emulate a connectivity status change
        val mockIntent = mockk<Intent>()
        every { mockConnectivityManager.activeNetworkInfo?.isConnected } returns true
        every { mockConnectivityManager.activeNetworkInfo?.type } returns ConnectivityManager.TYPE_MOBILE
        service.onReceive(context, mockIntent)

        assertEquals(NetworkStatus.WAN, networkStatus)

        // remove listener and call onReceive again
        service.removeNetworkConnectivityListener(listener)
        every { mockConnectivityManager.activeNetworkInfo?.type } returns ConnectivityManager.TYPE_WIFI
        service.onReceive(context, mockIntent)

        assertEquals(NetworkStatus.WAN, networkStatus)
    }
}

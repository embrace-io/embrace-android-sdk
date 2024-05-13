package io.embrace.android.embracesdk

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import com.google.common.util.concurrent.MoreExecutors
import io.embrace.android.embracesdk.capture.connectivity.EmbraceNetworkConnectivityService
import io.embrace.android.embracesdk.capture.connectivity.NetworkConnectivityListener
import io.embrace.android.embracesdk.comms.delivery.NetworkStatus
import io.embrace.android.embracesdk.fakes.FakeClock
import io.embrace.android.embracesdk.fakes.injection.fakeDataSourceModule
import io.embrace.android.embracesdk.fakes.system.mockContext
import io.embrace.android.embracesdk.logging.EmbLogger
import io.embrace.android.embracesdk.logging.EmbLoggerImpl
import io.embrace.android.embracesdk.worker.BackgroundWorker
import io.mockk.clearAllMocks
import io.mockk.every
import io.mockk.mockk
import io.mockk.unmockkAll
import io.mockk.verify
import org.junit.After
import org.junit.AfterClass
import org.junit.Before
import org.junit.BeforeClass
import org.junit.Test

@Suppress("DEPRECATION")
internal class EmbraceNetworkConnectivityServiceTest {

    private lateinit var service: EmbraceNetworkConnectivityService

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
            context = mockContext()
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
            fakeClock,
            worker,
            logger,
            mockConnectivityManager,
        ) { fakeDataSourceModule() }
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
        verify { context.registerReceiver(service, any()) }
        service.close()
        verify { context.unregisterReceiver(service) }
    }

    @Test
    fun `test listener get notified when connectivity status changes to WIFI`() {
        // add the connectivity listener
        val listener = mockk<NetworkConnectivityListener>()
        service.addNetworkConnectivityListener(listener)

        // call onReceive to emulate a connectivity status change
        val mockIntent = mockk<Intent>()
        every { mockConnectivityManager.activeNetworkInfo?.isConnected } returns true
        every { mockConnectivityManager.activeNetworkInfo?.type } returns ConnectivityManager.TYPE_WIFI
        service.onReceive(context, mockIntent)

        verify(exactly = 1) { listener.onNetworkConnectivityStatusChanged(NetworkStatus.WIFI) }
    }

    @Test
    fun `test listener get notified when connectivity status changes to MOBILE`() {
        // add the connectivity listener
        val listener = mockk<NetworkConnectivityListener>()
        service.addNetworkConnectivityListener(listener)

        // call onReceive to emulate a connectivity status change
        val mockIntent = mockk<Intent>()
        every { mockConnectivityManager.activeNetworkInfo?.isConnected } returns true
        every { mockConnectivityManager.activeNetworkInfo?.type } returns ConnectivityManager.TYPE_MOBILE
        service.onReceive(context, mockIntent)

        verify(exactly = 1) { listener.onNetworkConnectivityStatusChanged(NetworkStatus.WAN) }
    }

    @Test
    fun `test listener get notified when connectivity status changes to no connectivity`() {
        // add the connectivity listener
        val listener = mockk<NetworkConnectivityListener>()
        service.addNetworkConnectivityListener(listener)

        // call onReceive to emulate a connectivity status change
        val mockIntent = mockk<Intent>()
        every { mockConnectivityManager.activeNetworkInfo?.isConnected } returns false
        service.onReceive(context, mockIntent)

        verify(exactly = 1) { listener.onNetworkConnectivityStatusChanged(NetworkStatus.NOT_REACHABLE) }
    }

    @Test
    fun `test listener get notified when connectivity status changes and no info obtained`() {
        // add the connectivity listener
        val listener = mockk<NetworkConnectivityListener>()
        service.addNetworkConnectivityListener(listener)

        // call onReceive to emulate a connectivity status change
        val mockIntent = mockk<Intent>()
        every { mockConnectivityManager.activeNetworkInfo } throws Exception("")
        service.onReceive(context, mockIntent)

        verify(exactly = 1) { listener.onNetworkConnectivityStatusChanged(NetworkStatus.UNKNOWN) }
    }

    @Test
    fun `test listener get notified when connectivity status changes and not notified when removed`() {
        // add the connectivity listener
        val listener = mockk<NetworkConnectivityListener>()
        service.addNetworkConnectivityListener(listener)

        // call onReceive to emulate a connectivity status change
        val mockIntent = mockk<Intent>()
        every { mockConnectivityManager.activeNetworkInfo?.isConnected } returns true
        every { mockConnectivityManager.activeNetworkInfo?.type } returns ConnectivityManager.TYPE_MOBILE
        service.onReceive(context, mockIntent)

        verify(exactly = 1) { listener.onNetworkConnectivityStatusChanged(NetworkStatus.WAN) }

        // remove listener and call onReceive again
        service.removeNetworkConnectivityListener(listener)
        service.onReceive(context, mockIntent)

        verify(exactly = 1) { listener.onNetworkConnectivityStatusChanged(any()) }
    }
}

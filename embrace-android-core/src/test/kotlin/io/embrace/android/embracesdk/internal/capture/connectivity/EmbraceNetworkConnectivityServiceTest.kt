package io.embrace.android.embracesdk.internal.capture.connectivity

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import io.embrace.android.embracesdk.fakes.FakeClock
import io.embrace.android.embracesdk.fakes.fakeBackgroundWorker
import io.embrace.android.embracesdk.internal.logging.InternalLogger
import io.embrace.android.embracesdk.internal.logging.InternalLoggerImpl
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
    private lateinit var testListener: NetworkConnectivityListener
    private var status: ConnectivityStatus? = null

    companion object {
        private lateinit var context: Context
        private lateinit var logger: InternalLogger
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
            logger = InternalLoggerImpl()
            mockConnectivityManager = mockk()
            fakeClock = FakeClock()
            worker = fakeBackgroundWorker()
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

        testListener = NetworkConnectivityListener { status -> this@EmbraceNetworkConnectivityServiceTest.status = status }
        service.addNetworkConnectivityListener(testListener)
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
        // call onReceive to emulate a connectivity status change
        val mockIntent = mockk<Intent>()
        every { mockConnectivityManager.activeNetworkInfo?.isConnected } returns true
        every { mockConnectivityManager.activeNetworkInfo?.type } returns ConnectivityManager.TYPE_WIFI
        service.onReceive(context, mockIntent)
        assertEquals(OptimisticWifi, status)
    }

    @Test
    fun `test listener get notified when connectivity status changes to MOBILE`() {
        // call onReceive to emulate a connectivity status change
        val mockIntent = mockk<Intent>()
        every { mockConnectivityManager.activeNetworkInfo?.isConnected } returns true
        every { mockConnectivityManager.activeNetworkInfo?.type } returns ConnectivityManager.TYPE_MOBILE
        service.onReceive(context, mockIntent)
        assertEquals(OptimisticWan, status)
    }

    @Test
    fun `test listener get notified when connectivity status changes to no connectivity`() {
        // call onReceive to emulate a connectivity status change
        val mockIntent = mockk<Intent>()
        every { mockConnectivityManager.activeNetworkInfo?.isConnected } returns false
        service.onReceive(context, mockIntent)
        assertEquals(ConnectivityStatus.None, status)
    }

    @Test
    fun `test listener get notified when connectivity status changes and no info obtained`() {
        // call onReceive to emulate a connectivity status change
        val mockIntent = mockk<Intent>()
        every { mockConnectivityManager.activeNetworkInfo } throws Exception("")
        service.onReceive(context, mockIntent)
        assertEquals(OptimisticUnknown, status)
    }

    @Test
    fun `test listener get notified when connectivity status changes and not notified when removed`() {
        // call onReceive to emulate a connectivity status change
        val mockIntent = mockk<Intent>()
        every { mockConnectivityManager.activeNetworkInfo?.isConnected } returns true
        every { mockConnectivityManager.activeNetworkInfo?.type } returns ConnectivityManager.TYPE_MOBILE
        service.onReceive(context, mockIntent)

        assertEquals(OptimisticWan, status)

        // remove listener and call onReceive again
        service.removeNetworkConnectivityListener(testListener)
        every { mockConnectivityManager.activeNetworkInfo?.type } returns ConnectivityManager.TYPE_WIFI
        service.onReceive(context, mockIntent)

        assertEquals(OptimisticWan, status)
    }
}

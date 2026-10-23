package io.embrace.android.embracesdk.internal.capture.connectivity

import android.net.ConnectivityManager
import android.net.FakeConnectivityManager
import android.net.FakeNetwork
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkCapabilities.NET_CAPABILITY_INTERNET
import android.net.NetworkCapabilities.NET_CAPABILITY_VALIDATED
import android.net.NetworkCapabilities.TRANSPORT_CELLULAR
import android.net.NetworkCapabilities.TRANSPORT_WIFI
import io.embrace.android.embracesdk.concurrency.BlockingScheduledExecutorService
import io.embrace.android.embracesdk.fakes.fakeBackgroundWorker
import io.embrace.android.embracesdk.internal.logging.ErrorHandlerImpl
import io.embrace.android.embracesdk.internal.worker.BackgroundWorker
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

internal class NetworkCallbackConnectivityServiceTest {

    private lateinit var service: NetworkCallbackConnectivityService
    private lateinit var connectivityManager: FakeConnectivityManager
    private lateinit var lazyConnectivityManager: Lazy<ConnectivityManager?>
    private lateinit var receivedConnectivityStatuses: MutableList<ConnectivityStatus>
    private val networkConnectivityListener =
        NetworkConnectivityListener { status -> receivedConnectivityStatuses.add(status) }

    @Before
    fun setUp() {
        connectivityManager = FakeConnectivityManager()
        receivedConnectivityStatuses = mutableListOf()
        lazyConnectivityManager = lazy { connectivityManager }
        service = createService(fakeBackgroundWorker(), lazyConnectivityManager)
    }

    @Test
    fun `connectivity manager is not obtained when service is constructed`() {
        val lazyManager = lazy { connectivityManager }
        createService(fakeBackgroundWorker(), lazyManager)
        assertFalse(lazyManager.isInitialized())
    }

    @Test
    fun `connectivity manager is obtained on the background worker rather than the calling thread`() {
        val executor = BlockingScheduledExecutorService()
        val lazyManager = lazy { connectivityManager }
        createService(BackgroundWorker(executor), lazyManager).register()

        assertFalse(lazyManager.isInitialized())
        executor.runCurrentlyBlocked()
        assertTrue(lazyManager.isInitialized())
    }

    @Test
    fun `register calls registerDefaultNetworkCallback with service as callback`() {
        service.register()
        assertTrue(connectivityManager.networkCallbacks.contains(service))
    }

    @Test
    fun `adding listener before registration`() {
        service.addNetworkConnectivityListener(networkConnectivityListener)
        service.register()
        assertEquals(ConnectivityStatus.Unverified, receivedConnectivityStatuses.single())
    }

    @Test
    fun `adding listener after registration`() {
        service.register()
        service.addNetworkConnectivityListener(networkConnectivityListener)
        assertEquals(ConnectivityStatus.Unverified, receivedConnectivityStatuses.single())
    }

    @Test
    fun `register called twice only registers once`() {
        service.register()
        service.register()
        service.addNetworkConnectivityListener(networkConnectivityListener)
        assertEquals(1, connectivityManager.networkCallbacks.size)
    }

    @Test
    fun `exception during register is caught and does not propagate`() {
        createService(fakeBackgroundWorker(), lazyOf<ConnectivityManager?>(null)).register()
    }

    @Test
    fun `onAvailable makes doesn't change connectivity status but onCapabilitiesChanged does`() {
        initService()
        val newNetwork = setActiveNetwork(TestNetwork.CONNECTED_WIFI)
        service.onAvailable(newNetwork)
        assertEquals(ConnectivityStatus.Unverified, receivedConnectivityStatuses.single())
        service.onCapabilitiesChanged(newNetwork, TestNetwork.CONNECTED_WIFI.capabilities)
        assertEquals(2, receivedConnectivityStatuses.size)
        assertEquals(TestNetwork.CONNECTED_WIFI.status, receivedConnectivityStatuses.last())
    }

    @Test
    fun `switching from a connected to a disconnected network of the same type will update listener`() {
        initWithNetwork(TestNetwork.CONNECTED_WIFI)
        assertEquals(TestNetwork.CONNECTED_WIFI.status, receivedConnectivityStatuses.single())
        val newerNetwork = setActiveNetwork(TestNetwork.DISCONNECTED_WIFI)
        service.onAvailable(newerNetwork)
        service.onCapabilitiesChanged(newerNetwork, TestNetwork.DISCONNECTED_WIFI.capabilities)
        assertEquals(2, receivedConnectivityStatuses.size)
        assertEquals(TestNetwork.DISCONNECTED_WIFI.status, receivedConnectivityStatuses.last())
    }

    @Test
    fun `switching to all the connections`() {
        initService()
        assertEquals(ConnectivityStatus.Unverified, receivedConnectivityStatuses.last())
        TestNetwork.entries.forEach { testNetwork ->
            setActiveNetwork(testNetwork).let { network ->
                service.onAvailable(network)
                service.onCapabilitiesChanged(network, testNetwork.capabilities)
            }
            assertEquals(testNetwork.status, receivedConnectivityStatuses.last())
        }
    }

    @Test
    fun `switching from unverified to connected unknown will result in a dispatch`() {
        initService()
        assertEquals(ConnectivityStatus.Unverified, receivedConnectivityStatuses.last())
        setActiveNetwork(TestNetwork.CONNECTED_UNKNOWN).let { network ->
            service.onAvailable(network)
            service.onCapabilitiesChanged(network, TestNetwork.CONNECTED_UNKNOWN.capabilities)
        }
        assertEquals(2, receivedConnectivityStatuses.size)
        assertEquals(TestNetwork.CONNECTED_UNKNOWN.status, receivedConnectivityStatuses.last())
    }

    @Test
    fun `onCapabilitiesChanged for non-current network does not dispatch`() {
        val originalNetwork = initWithNetwork(TestNetwork.CONNECTED_WIFI)
        service.onAvailable(originalNetwork)
        assertNotEquals(originalNetwork, setActiveNetwork(TestNetwork.CONNECTED_WAN))
        service.onCapabilitiesChanged(originalNetwork, TestNetwork.CONNECTED_WIFI.capabilities)
        assertEquals(TestNetwork.CONNECTED_WIFI.status, receivedConnectivityStatuses.single())
    }

    @Test
    fun `onLost for current network will switch connectivity status to none`() {
        val network = initWithNetwork(TestNetwork.CONNECTED_WIFI)
        service.onLost(network)
        assertEquals(2, receivedConnectivityStatuses.size)
        assertEquals(TestNetwork.NONE.status, receivedConnectivityStatuses.last())
    }

    @Test
    fun `onLost for current network make subsequent capability changes for that network not be processed`() {
        initWithNetwork(TestNetwork.CONNECTED_WIFI)
        val newNetwork = setActiveNetwork(TestNetwork.CONNECTED_WAN)
        service.onAvailable(newNetwork)
        service.onLost(newNetwork)
        service.onCapabilitiesChanged(newNetwork, TestNetwork.CONNECTED_WAN.capabilities)
        assertEquals(TestNetwork.NONE.status, receivedConnectivityStatuses.last())
    }

    @Test
    fun `onLost for non-current network is ignored`() {
        val oldNetwork = initWithNetwork(TestNetwork.CONNECTED_WIFI)
        val newNetwork = setActiveNetwork(TestNetwork.CONNECTED_WAN)
        service.onAvailable(newNetwork)
        service.onLost(oldNetwork)
        assertEquals(TestNetwork.CONNECTED_WIFI.status, receivedConnectivityStatuses.last())
        service.onCapabilitiesChanged(newNetwork, TestNetwork.CONNECTED_WAN.capabilities)
        assertEquals(TestNetwork.CONNECTED_WAN.status, receivedConnectivityStatuses.last())
    }

    @Test
    fun `switching to the same or different networks with equivalent connectivity status will not result in multiple dispatches`() {
        initWithNetwork(TestNetwork.CONNECTED_WIFI)
        val newWifi = setActiveNetwork(TestNetwork.CONNECTED_WIFI)
        service.onAvailable(newWifi)
        service.onCapabilitiesChanged(newWifi, TestNetwork.CONNECTED_WIFI.capabilities)
        service.onCapabilitiesChanged(newWifi, TestNetwork.CONNECTED_WIFI.capabilities)
        assertEquals(1, receivedConnectivityStatuses.size)
    }

    @Test
    fun `addNetworkConnectivityListener gets current status upon addition`() {
        val newNetwork = setActiveNetwork(TestNetwork.CONNECTED_WIFI)
        service.register()
        service.onAvailable(newNetwork)
        service.onCapabilitiesChanged(newNetwork, TestNetwork.CONNECTED_WIFI.capabilities)
        service.addNetworkConnectivityListener(networkConnectivityListener)
        assertEquals(TestNetwork.CONNECTED_WIFI.status, receivedConnectivityStatuses.single())
    }

    @Test
    fun `removeNetworkConnectivityListener stops updates`() {
        initService()
        service.removeNetworkConnectivityListener(networkConnectivityListener)
        val newNetwork = setActiveNetwork(TestNetwork.CONNECTED_WIFI)
        service.onAvailable(newNetwork)
        service.onCapabilitiesChanged(newNetwork, TestNetwork.CONNECTED_WIFI.capabilities)
        assertEquals(ConnectivityStatus.Unverified, receivedConnectivityStatuses.single())
    }

    @Test
    fun `close calls unregisterNetworkCallback with service as callback`() {
        service.register()
        assertTrue(connectivityManager.networkCallbacks.contains(service))
        service.close()
        assertFalse(connectivityManager.networkCallbacks.contains(service))
    }

    @Test
    fun `close without register is a no-op`() {
        service.close()
        assertFalse(connectivityManager.networkCallbacks.contains(service))
    }

    @Test
    fun `register after close is a no-op`() {
        service.register()
        service.close()
        service.register()
        assertFalse(connectivityManager.networkCallbacks.contains(service))
    }

    @Test
    fun `close before queued register runs prevents registration`() {
        val executor = BlockingScheduledExecutorService(blockingMode = true)
        val service = createService(BackgroundWorker(executor), lazyConnectivityManager)
        service.register()
        service.close()
        executor.runCurrentlyBlocked()
        assertFalse(connectivityManager.networkCallbacks.contains(service))
    }

    @Test
    fun `close called twice does not throw`() {
        service.register()
        service.close()
        service.close()
        assertFalse(connectivityManager.networkCallbacks.contains(service))
    }

    private fun createService(
        backgroundWorker: BackgroundWorker,
        connectivityManager: Lazy<ConnectivityManager?>,
    ) = NetworkCallbackConnectivityService(
        backgroundWorker,
        ErrorHandlerImpl(),
        connectivityManager,
    ) { caps ->
        val network = TestNetwork.entries.single { it.capabilities === caps }
        connectivityStatus(network.capabilityIds::contains, network.transportIds::contains)
    }

    private fun initWithNetwork(network: TestNetwork): Network {
        val newNetwork = setActiveNetwork(network)
        initService()
        return newNetwork
    }

    private fun initService() {
        service.register()
        service.addNetworkConnectivityListener(networkConnectivityListener)
    }

    private fun setActiveNetwork(testNetwork: TestNetwork): Network {
        val network = FakeNetwork()
        connectivityManager.active = network
        connectivityManager.capabilities[network] = testNetwork.capabilities
        return network
    }

    private enum class TestNetwork(
        val capabilityIds: Set<Int>,
        val transportIds: Set<Int>,
        val status: ConnectivityStatus,
    ) {
        CONNECTED_WIFI(
            setOf(NET_CAPABILITY_INTERNET, NET_CAPABILITY_VALIDATED),
            setOf(TRANSPORT_WIFI),
            ConnectivityStatus.Wifi(true),
        ),
        DISCONNECTED_WIFI(setOf(NET_CAPABILITY_INTERNET), setOf(TRANSPORT_WIFI), ConnectivityStatus.Wifi(false)),
        CONNECTED_WAN(
            setOf(NET_CAPABILITY_INTERNET, NET_CAPABILITY_VALIDATED),
            setOf(TRANSPORT_CELLULAR),
            ConnectivityStatus.Wan(true),
        ),
        DISCONNECTED_WAN(setOf(NET_CAPABILITY_INTERNET), setOf(TRANSPORT_CELLULAR), ConnectivityStatus.Wan(false)),
        CONNECTED_UNKNOWN(
            setOf(NET_CAPABILITY_INTERNET, NET_CAPABILITY_VALIDATED),
            emptySet(),
            ConnectivityStatus.Unknown(true),
        ),
        DISCONNECTED_UNKNOWN(setOf(NET_CAPABILITY_INTERNET), emptySet(), ConnectivityStatus.Unknown(false)),
        NONE(emptySet(), emptySet(), ConnectivityStatus.None),
        ;

        val capabilities = NetworkCapabilities()
    }
}
